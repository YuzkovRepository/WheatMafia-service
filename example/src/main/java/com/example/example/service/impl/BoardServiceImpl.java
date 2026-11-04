package com.example.example.service.impl;

import com.example.example.entity.Factory;
import com.example.example.entity.ProductRecipe;
import com.example.example.entity.UserSession;
import com.example.example.entity.board.Board;
import com.example.example.entity.board.PlacedObject;
import com.example.example.entity.board.Storage;
import com.example.example.exception.CommonException;
import com.example.example.repository.FactoryRepository;
import com.example.example.repository.ProductRecipeRepository;
import com.example.example.repository.ResearchRepository;
import com.example.example.repository.UserSessionRepository;
import com.example.example.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final UserSessionRepository userSessionRepository;
    private final ProductRecipeRepository recipeRepository;
    private final ResearchRepository researchRepository;
    private final FactoryRepository factoryRepository;
    private final JsonMapper jsonMapper;
    private final SimpMessagingTemplate messagingTemplate;

    // ---------- чтение ----------

    @Override
    @Transactional
    public Board getBoardBySessionAndUser(String sessionCode, Long userId) {
        UserSession us = userSessionRepository
                .findBySessionCodeAndUserId(sessionCode.toUpperCase(), userId)
                .orElseThrow(() -> new CommonException("UserSession not found"));
        loadBoard(us);
        return readBoard(us);
    }

    @Override
    @Transactional
    public Board getBoardByUserSession(Long userSessionId) {
        UserSession us = userSessionRepository.findById(userSessionId)
                .orElseThrow(() -> new CommonException("UserSession not found"));
        loadBoard(us);
        return readBoard(us);
    }

    // ---------- посадка / стройка ----------

    @Override
    @Transactional
    public PlacedObject plant(UserSession us, int row, int col,
                              int width, int height, String crop, int growSeconds) {
        // проверка: культура должна быть исследована (открыта) в этом регионе
        boolean cropAllowed = us.getUnlockedRecipes().stream()
                .anyMatch(k -> k.equals(crop + ":" + crop));
        if (!cropAllowed) {
            throw new CommonException("Crop not researched: " + crop);
        }
        Board board = ensureBoard(us);
        PlacedObject obj = board.placeCrop(row, col, width, height, crop, growSeconds);
        persist(us, board);
        publishAfterCommit(us, board);
        return obj;
    }

    /**
     * Здание открыто, если исследовано то, что его открывает (Factory.requiredResearch —
     * ровно то же правило, по которому каталог показывает здания в меню). Здание без
     * requiredResearch доступно сразу. Старое правило (есть открытый рецепт в здании) оставлено.
     */
    private boolean isBuildingUnlocked(UserSession us, String building) {
        if (us.getUnlockedRecipes().stream().anyMatch(k -> k.startsWith(building + ":"))) return true;

        String required = factoryRepository.findAll().stream()
                .filter(f -> building.equals(f.getCode()))
                .map(Factory::getRequiredResearch)
                .findFirst().orElse(null);
        if (required == null) return true;

        return researchRepository.findAll().stream()
                .filter(r -> required.equals(r.getCode()))
                .anyMatch(r -> us.getUnlockedRecipes()
                        .contains(r.getFactoryCode() + ":" + r.getOutputCode()));
    }

    @Override
    @Transactional
    public PlacedObject build(UserSession us, int row, int col,
                              int width, int height, String building, int level) {
        if (!isBuildingUnlocked(us, building)) {
            throw new CommonException("Building not researched yet: " + building);
        }
        Board board = ensureBoard(us);
        PlacedObject obj = board.placeBuilding(row, col, width, height, building, level);
        persist(us, board);
        publishAfterCommit(us, board);
        return obj;
    }

    private Board loadBoard(UserSession us) {
        Board board = us.getBoard();
        if (board == null) board = new Board();
        if (board.ensureWarehouse()) {
            us.setBoard(board);
            userSessionRepository.save(us);
        }
        return board;
    }

    // ---------- сбор урожая ----------

    @Override
    @Transactional
    public PlacedObject harvest(UserSession us, long objectId) {
        Board board = ensureBoard(us);
        Storage storage = ensureStorage(us);

        PlacedObject current = board.findObject(objectId)
                .orElseThrow(() -> new CommonException("Object not found"));
        if (current.getKind() != PlacedObject.Kind.CROP) {
            throw new CommonException("Not a crop");
        }
        String code = current.getCrop();
        int qty = 1;
        // место на складе проверяем ДО того, как культура начнёт расти заново
        if (!storage.canAdd(code, qty)) {
            throw new CommonException("Storage full");
        }

        PlacedObject obj;
        try {
            obj = board.harvest(objectId); // культура остаётся и растёт заново
        } catch (IllegalStateException e) {
            throw new CommonException(e.getMessage());
        }
        storage.add(code, qty);
        us.setStorage(storage);

        persist(us, board);
        publishAfterCommit(us, board);
        return obj;
    }

    // ---------- производство ----------

    /** Число ячеек производства на одном здании (идущие, ожидающие и готовые к выдаче). */
    public static final int MAX_QUEUE = 3;

    @Override
    @Transactional
    public PlacedObject startProduction(UserSession us, long objectId, String outputCode) {
        Board board = ensureBoard(us);
        PlacedObject obj = board.findObject(objectId)
                .orElseThrow(() -> new CommonException("Object not found"));

        if (obj.getKind() != PlacedObject.Kind.BUILDING) {
            throw new CommonException("Not a building");
        }

        Instant now = Instant.now();
        if (obj.getBlockedUntil() != null && obj.getBlockedUntil().isAfter(now)) {
            throw new CommonException("Building is on strike");
        }
        normalize(obj, now);
        List<PlacedObject.QueuedJob> jobs = obj.getQueue();
        if (jobs.size() >= MAX_QUEUE) {
            throw new CommonException("Queue is full");
        }

        String factoryCode = obj.getBuilding();

        ProductRecipe recipe;
        if (outputCode == null || outputCode.isBlank()) {
            List<ProductRecipe> all = recipeRepository.findByFactoryCode(factoryCode);
            if (all.isEmpty()) throw new CommonException("No recipe for " + factoryCode);
            List<ProductRecipe> allowed = all.stream()
                    .filter(r -> us.getUnlockedRecipes()
                            .contains(factoryCode + ":" + r.getOutput().getCode()))
                    .toList();
            if (allowed.isEmpty()) throw new CommonException("No researched recipe for " + factoryCode);
            if (allowed.size() > 1) throw new CommonException("Multiple recipes, specify outputCode");
            recipe = allowed.get(0);
        } else {
            recipe = recipeRepository
                    .findByFactoryCodeAndOutputCode(factoryCode, outputCode)
                    .orElseThrow(() -> new CommonException("No recipe for " + factoryCode + " → " + outputCode));
        }

        String recipeKey = factoryCode + ":" + recipe.getOutput().getCode();
        if (!us.getUnlockedRecipes().contains(recipeKey)) {
            throw new CommonException("Recipe not researched: " + recipeKey);
        }

        Storage storage = ensureStorage(us);
        for (ProductRecipe.RecipeInput in : recipe.getInputs()) {
            if (!storage.has(in.getCode(), in.getQty())) {
                throw new CommonException("Not enough " + in.getCode());
            }
        }
        for (ProductRecipe.RecipeInput in : recipe.getInputs()) {
            storage.remove(in.getCode(), in.getQty());
        }

        // стартует сразу, если завод свободен, иначе — когда закончится предыдущий продукт
        PlacedObject.QueuedJob job = jobOf(recipeKey, recipe);
        Instant start = now;
        if (!jobs.isEmpty()) {
            Instant prevEnd = jobs.get(jobs.size() - 1).getReadyAt();
            if (prevEnd != null && prevEnd.isAfter(now)) start = prevEnd;
        }
        job.setStartedAt(start);
        job.setReadyAt(start.plusSeconds(job.getTime()));
        jobs.add(job);

        us.setStorage(storage);
        persist(us, board);
        publishAfterCommit(us, board);
        return obj;
    }

    /** Забрать первый готовый продукт. */
    @Override
    @Transactional
    public PlacedObject collectProduction(UserSession us, long objectId) {
        return collectSlot(us, objectId, -1);
    }

    /** Забрать продукт из конкретной ячейки (index) независимо от остальных; index &lt; 0 — первый готовый. */
    @Override
    @Transactional
    public PlacedObject collectSlot(UserSession us, long objectId, int index) {
        Board board = ensureBoard(us);
        PlacedObject obj = board.findObject(objectId)
                .orElseThrow(() -> new CommonException("Object not found"));

        Instant now = Instant.now();
        normalize(obj, now);
        List<PlacedObject.QueuedJob> jobs = obj.getQueue();

        int i = index;
        if (i < 0) {
            for (int k = 0; k < jobs.size(); k++) {
                if (!now.isBefore(jobs.get(k).getReadyAt())) { i = k; break; }
            }
            if (i < 0) throw new CommonException("Nothing to collect");
        }
        if (i >= jobs.size()) throw new CommonException("Nothing to collect");

        PlacedObject.QueuedJob job = jobs.get(i);
        if (now.isBefore(job.getReadyAt())) {
            throw new CommonException("Not ready yet");
        }

        Storage storage = ensureStorage(us);
        int qty = job.getOutputQty() == null ? 1 : job.getOutputQty();
        if (!storage.canAdd(job.getOutput(), qty)) {
            throw new CommonException("Storage full");
        }
        storage.add(job.getOutput(), qty);
        jobs.remove(i);

        us.setStorage(storage);
        persist(us, board);
        publishAfterCommit(us, board);
        return obj;
    }

    /** Убрать ещё не начатый продукт из очереди: ингредиенты возвращаются на склад. */
    @Override
    @Transactional
    public PlacedObject cancelQueued(UserSession us, long objectId, int index) {
        Board board = ensureBoard(us);
        PlacedObject obj = board.findObject(objectId)
                .orElseThrow(() -> new CommonException("Object not found"));

        Instant now = Instant.now();
        normalize(obj, now);
        List<PlacedObject.QueuedJob> jobs = obj.getQueue();
        if (index < 0 || index >= jobs.size()) {
            throw new CommonException("Queue item not found");
        }
        PlacedObject.QueuedJob job = jobs.get(index);
        if (!now.isBefore(job.getStartedAt())) {
            throw new CommonException("Production already started");
        }

        Storage storage = ensureStorage(us);
        int total = job.getInputs().values().stream().mapToInt(Integer::intValue).sum();
        if (storage.used() + total > storage.getCapacity()) {
            throw new CommonException("Storage full");
        }
        job.getInputs().forEach(storage::add);
        jobs.remove(index);

        // ожидающие продукты подтягиваются вперёд
        Instant prevEnd = null;
        for (PlacedObject.QueuedJob j : jobs) {
            if (now.isBefore(j.getStartedAt())) {
                Instant st = (prevEnd != null && prevEnd.isAfter(now)) ? prevEnd : now;
                j.setStartedAt(st);
                j.setReadyAt(st.plusSeconds(j.getTime()));
            }
            prevEnd = j.getReadyAt();
        }

        us.setStorage(storage);
        persist(us, board);
        publishAfterCommit(us, board);
        return obj;
    }

    /**
     * Приводит здание к модели «ячеек»: старое одиночное производство (productionXxx) становится
     * первой ячейкой, а у ячеек без времени расставляется расписание.
     */
    private void normalize(PlacedObject obj, Instant now) {
        List<PlacedObject.QueuedJob> jobs = obj.getQueue();
        if (obj.getProductionReadyAt() != null) {
            PlacedObject.QueuedJob j = new PlacedObject.QueuedJob();
            j.setRecipeCode(obj.getRecipeCode());
            j.setOutput(obj.getProductionOutput());
            j.setOutputQty(obj.getProductionOutputQty());
            j.setTime(obj.getProductionTime() == null ? 0 : obj.getProductionTime());
            j.setStartedAt(obj.getProductionStartedAt() == null ? now : obj.getProductionStartedAt());
            j.setReadyAt(obj.getProductionReadyAt());
            jobs.add(0, j);
            clearProduction(obj);
        }
        Instant prevEnd = null;
        for (PlacedObject.QueuedJob j : jobs) {
            if (j.getStartedAt() == null || j.getReadyAt() == null) {
                Instant st = (prevEnd != null && prevEnd.isAfter(now)) ? prevEnd : now;
                j.setStartedAt(st);
                j.setReadyAt(st.plusSeconds(j.getTime() == null ? 0 : j.getTime()));
            }
            prevEnd = j.getReadyAt();
        }
    }

    private PlacedObject.QueuedJob jobOf(String recipeKey, ProductRecipe recipe) {
        PlacedObject.QueuedJob j = new PlacedObject.QueuedJob();
        j.setRecipeCode(recipeKey);
        j.setOutput(recipe.getOutput().getCode());
        j.setOutputQty(recipe.getOutputQty());
        j.setTime(recipe.getProductionTime());
        Map<String, Integer> inputs = new LinkedHashMap<>();
        for (ProductRecipe.RecipeInput in : recipe.getInputs()) {
            inputs.merge(in.getCode(), in.getQty(), Integer::sum);
        }
        j.setInputs(inputs);
        return j;
    }

    private void clearProduction(PlacedObject obj) {
        obj.setProductionStartedAt(null);
        obj.setProductionReadyAt(null);
        obj.setProductionInput(null);
        obj.setProductionInputQty(null);
        obj.setProductionOutput(null);
        obj.setProductionOutputQty(null);
        obj.setProductionTime(null);
        obj.setRecipeCode(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Storage getStorage(UserSession us) {
        return us.getStorage() != null ? us.getStorage() : new Storage();
    }

    // ---------- внутреннее ----------

    private Board readBoard(UserSession us) {
        Board board = us.getBoard();
        boolean changed = false;
        if (board == null) { board = new Board(); changed = true; }
        if (board.getSize() < Board.DEFAULT_SIZE) { board.resize(Board.DEFAULT_SIZE); changed = true; }
        if (board.ensureWarehouse()) changed = true;
        if (changed) persist(us, board);
        return us.getBoard();
    }

    private Board ensureBoard(UserSession us) {
        return readBoard(us);
    }

    @Override
    @Transactional
    public PlacedObject move(UserSession us, long objectId, int row, int col) {
        Board board = ensureBoard(us);
        PlacedObject obj = board.findObject(objectId)
                .orElseThrow(() -> new CommonException("Object not found"));
        // двигать можно культуры и здания; торговца — нельзя
        if (obj.getKind() == PlacedObject.Kind.BUILDING && "trader".equals(obj.getBuilding())) {
            throw new CommonException("This object cannot be moved");
        }
        try {
            board.moveObject(objectId, row, col);
        } catch (IllegalStateException e) {
            throw new CommonException(e.getMessage());
        }
        persist(us, board);
        publishAfterCommit(us, board);
        return obj;
    }

    /** Убрать культуру с поля (здания убирать нельзя). */
    @Override
    @Transactional
    public PlacedObject remove(UserSession us, long objectId) {
        Board board = ensureBoard(us);
        PlacedObject obj = board.findObject(objectId)
                .orElseThrow(() -> new CommonException("Object not found"));
        if (obj.getKind() != PlacedObject.Kind.CROP) {
            throw new CommonException("This object cannot be removed");
        }
        board.removeObject(objectId);
        persist(us, board);
        publishAfterCommit(us, board);
        return obj;
    }

    private Storage ensureStorage(UserSession us) {
        Storage s = us.getStorage();
        if (s == null) {
            s = new Storage();
            us.setStorage(s);
        }
        return s;
    }

    private void persist(UserSession us, Board board) {
        Board copy = jsonMapper.convertValue(board, Board.class);
        us.setBoard(copy);
        userSessionRepository.save(us);
    }

    private void publishAfterCommit(UserSession us, Board board) {
        Board snapshot = jsonMapper.convertValue(board, Board.class);
        String sessionCode = us.getSession().getSessionCode();
        Long userId = us.getUser().getUserId();
        String destination = "/topic/board." + sessionCode + "." + userId;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            messagingTemplate.convertAndSend(destination, snapshot);
                        }
                    }
            );
        } else {
            messagingTemplate.convertAndSend(destination, snapshot);
        }
    }

    private String formatInputs(Map<String, Integer> inputs) {
        return inputs.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
}