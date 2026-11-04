package com.example.example.service.impl;

import com.example.example.dto.DeputyDto;
import com.example.example.entity.DeputyDelivery;
import com.example.example.entity.Product;
import com.example.example.entity.Research;
import com.example.example.entity.Session;
import com.example.example.entity.UserSession;
import com.example.example.entity.board.Storage;
import com.example.example.exception.CommonException;
import com.example.example.repository.DeputyDeliveryRepository;
import com.example.example.repository.ProductRepository;
import com.example.example.repository.ResearchRepository;
import com.example.example.repository.SessionRepository;
import com.example.example.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeputyServiceImpl {

    private static final int FIRST_DISH_STAGE = 3;
    private static final String GRAND = "GRAND";
    private static final String GRAND_SUFFIX = "_grand_export";

    private final SessionRepository sessionRepository;
    private final UserSessionRepository userSessionRepository;
    private final ResearchRepository researchRepository;
    private final ProductRepository productRepository;
    private final DeputyDeliveryRepository deliveryRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;

    // ---------- чтение ----------

    @Transactional(readOnly = true)
    public DeputyDto.State state(String sessionCode) {
        Session session = sessionRepository.findBySessionCode(sessionCode.toUpperCase())
                .orElseThrow(() -> new CommonException("Комната не найдена"));
        return buildState(session);
    }

    // ---------- накормить депутата ----------

    @Transactional
    public DeputyDto.State feed(String sessionCode, Long userId, String productCode) {
        String code = sessionCode.toUpperCase();
        UserSession us = userSessionRepository.findBySessionCodeAndUserId(code, userId)
                .orElseThrow(() -> new CommonException("UserSession not found"));
        Session session = us.getSession();

        if (session.getStatus() == Session.Status.COMPLETED
                || deliveryRepository.existsBySessionCodeAndSlot(code, GRAND)) {
            throw new CommonException("Игра уже завершена");
        }
        if (productCode == null || productCode.isBlank()) {
            throw new CommonException("Не выбрано блюдо");
        }

        Map<String, Product> products = products();
        Product product = products.get(productCode);
        if (product == null) throw new CommonException("Продукт не найден");

        String slot;
        if (productCode.equals(grandCodeOf(us.getRegion()))) {
            slot = GRAND;
            for (int stage : requiredStages(products)) {
                if (!deliveryRepository.existsBySessionCodeAndUserIdAndSlot(code, userId, "S" + stage)) {
                    throw new CommonException("Сначала накормите депутата блюдом каждого этапа");
                }
            }
        } else if (productCode.endsWith(GRAND_SUFFIX)) {
            throw new CommonException("Депутат ждёт золотой экспорт вашей страны");
        } else {
            Integer stage = dishStages(products).get(productCode);
            if (stage == null) {
                throw new CommonException("Депутат принимает только готовые легальные блюда");
            }
            slot = "S" + stage;
            if (deliveryRepository.existsBySessionCodeAndUserIdAndSlot(code, userId, slot)) {
                throw new CommonException("Блюдо этого этапа уже сдано");
            }
            // заказ депутата: только конкретное блюдо, закреплённое за игроком и этапом
            String expected = orderedDish(us, stage, products);
            if (expected != null && !expected.equals(productCode)) {
                throw new CommonException("Депутат ждёт другое блюдо этого этапа");
            }
        }

        Storage storage = us.getStorage() == null
                ? new Storage()
                : jsonMapper.convertValue(us.getStorage(), Storage.class);
        if (!storage.has(productCode, 1)) {
            throw new CommonException("На складе нет: " + product.getTitle());
        }
        storage.remove(productCode, 1);
        us.setStorage(storage);
        userSessionRepository.save(us);

        DeputyDelivery d = new DeputyDelivery();
        d.setSessionCode(code);
        d.setUserId(userId);
        d.setSlot(slot);
        d.setProductCode(productCode);
        deliveryRepository.save(d);

        boolean won = GRAND.equals(slot);
        if (won) {
            session.setStatus(Session.Status.COMPLETED);
            sessionRepository.save(session);
        }

        DeputyDto.State state = buildState(session);
        publish(code, new DeputyDto.Event(won ? "WON" : "FED", userId,
                us.getUser().getNickName(), productCode, product.getTitle(), slot, state));
        return state;
    }

    /**
     * Заказ депутата для конкретного игрока и этапа — ОДНО детерминированно выбранное блюдо.
     * Выбор стабилен для (sessionCode, userId, stage), поэтому обновление страницы не меняет заказ.
     */
    private String orderedDish(UserSession us, int stage, Map<String, Product> products) {
        String region = us.getRegion();
        if (region == null) return null;

        List<Research> candidates = dishResearches(products).stream()
                .filter(r -> r.getStage() == stage)
                .filter(r -> region.equals(r.getRegion()))
                .sorted(Comparator.comparing(Research::getOutputCode))
                .toList();
        if (candidates.isEmpty()) return null;

        String seed = us.getSession().getSessionCode() + "|" + us.getUser().getUserId() + "|" + stage;
        int idx = Math.floorMod(stableHash(seed), candidates.size());
        return candidates.get(idx).getOutputCode();
    }

    private static int stableHash(String s) {
        int h = 0x811c9dc5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return h;
    }

    // ---------- расчёт состояния ----------

    private DeputyDto.State buildState(Session session) {
        String code = session.getSessionCode();
        Map<String, Product> products = products();
        List<Research> dishes = dishResearches(products);
        List<Integer> stages = dishes.stream().map(Research::getStage).distinct().sorted().toList();

        Map<String, Integer> dishStage = new LinkedHashMap<>();
        for (Research r : dishes) dishStage.merge(r.getOutputCode(), r.getStage(), Math::min);

        Map<Long, Map<String, DeputyDelivery>> bySlot = new HashMap<>();
        for (DeputyDelivery d : deliveryRepository.findBySessionCode(code)) {
            bySlot.computeIfAbsent(d.getUserId(), k -> new HashMap<>()).put(d.getSlot(), d);
        }

        List<UserSession> sessions = session.getUserSessions().stream()
                .sorted(Comparator.comparing(UserSession::getUserSessionId))
                .toList();

        List<DeputyDto.Player> players = new ArrayList<>();
        DeputyDto.Winner winner = null;

        for (UserSession us : sessions) {
            Long uid = us.getUser().getUserId();
            String region = us.getRegion();
            Map<String, DeputyDelivery> mine = bySlot.getOrDefault(uid, Map.of());

            List<DeputyDto.Step> steps = new ArrayList<>();
            for (int stage : stages) {
                String slot = "S" + stage;
                DeputyDelivery d = mine.get(slot);
                String orderCode = orderedDish(us, stage, products);
                // для несданного шага productCode — это заказ депутата (клиент рисует эмодзи по нему)
                String productCode = d != null ? d.getProductCode() : orderCode;
                steps.add(new DeputyDto.Step(
                        slot, stage, false, d != null,
                        productCode,
                        title(products, productCode),
                        examples(dishes, region, stage, orderCode, products)));
            }

            String grandCode = grandCodeOf(region);
            DeputyDelivery g = mine.get(GRAND);
            String grandProductCode = g != null ? g.getProductCode() : grandCode;
            steps.add(new DeputyDto.Step(GRAND, 0, true, g != null,
                    grandProductCode,
                    title(products, grandProductCode),
                    List.of()));

            int delivered = (int) steps.stream().filter(DeputyDto.Step::done).count();
            boolean finished = g != null;
            players.add(new DeputyDto.Player(uid, us.getUser().getNickName(), region, grandCode,
                    steps, delivered, steps.size(), finished));
            if (finished && winner == null) {
                winner = new DeputyDto.Winner(uid, us.getUser().getNickName(), region);
            }
        }

        String status = session.getStatus() != null ? session.getStatus().name() : "PENDING";
        return new DeputyDto.State(code, status, players, winner, dishStage);
    }

    // ---------- справочные данные ----------

    private Map<String, Product> products() {
        Map<String, Product> m = new HashMap<>();
        for (Product p : productRepository.findAll()) m.put(p.getCode(), p);
        return m;
    }

    private boolean isDish(Product p) {
        return p != null
                && p.getKind() == Product.ProductKind.GOODS
                && p.getLegality() == Product.Legality.LEGAL
                && !p.getCode().endsWith(GRAND_SUFFIX);
    }

    private List<Research> dishResearches(Map<String, Product> products) {
        return researchRepository.findAll().stream()
                .filter(r -> r.getOutputCode() != null && r.getStage() >= FIRST_DISH_STAGE)
                .filter(r -> isDish(products.get(r.getOutputCode())))
                .toList();
    }

    private Map<String, Integer> dishStages(Map<String, Product> products) {
        Map<String, Integer> m = new HashMap<>();
        for (Research r : dishResearches(products)) m.merge(r.getOutputCode(), r.getStage(), Math::min);
        return m;
    }

    private List<Integer> requiredStages(Map<String, Product> products) {
        return dishResearches(products).stream().map(Research::getStage).distinct().sorted().toList();
    }

    /**
     * Подсказка: похожие блюда этапа (для разнообразия), но заказ — ровно один {@code orderCode}.
     * Первым в списке идёт само заказанное блюдо, затем — другие того же региона и этапа.
     */
    private List<String> examples(List<Research> dishes, String region, int stage,
                                  String orderCode, Map<String, Product> products) {
        if (orderCode == null) return List.of();
        List<String> out = new ArrayList<>();
        out.add(title(products, orderCode));
        dishes.stream()
                .filter(r -> r.getStage() == stage && region != null && region.equals(r.getRegion()))
                .map(r -> title(products, r.getOutputCode()))
                .filter(t -> !out.contains(t))
                .sorted()
                .limit(3)
                .forEach(out::add);
        return out;
    }

    private String grandCodeOf(String region) {
        return region == null ? null : region.toLowerCase() + GRAND_SUFFIX;
    }

    private String title(Map<String, Product> products, String code) {
        Product p = code == null ? null : products.get(code);
        return p != null ? p.getTitle() : "Блюдо";
    }

    // ---------- рассылка ----------

    private void publish(String sessionCode, DeputyDto.Event event) {
        String destination = "/topic/deputy." + sessionCode;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(destination, event);
                }
            });
        } else {
            messagingTemplate.convertAndSend(destination, event);
        }
    }
}