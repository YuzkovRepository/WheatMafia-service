package com.example.example.service;

import com.example.example.entity.CouncilRound;
import com.example.example.entity.CouncilVote;
import com.example.example.entity.Product;
import com.example.example.entity.UserSession;
import com.example.example.entity.Wallet;
import com.example.example.entity.board.Board;
import com.example.example.entity.board.PlacedObject;
import com.example.example.entity.board.Storage;
import com.example.example.exception.CommonException;
import com.example.example.repository.CouncilPlayerRepository;
import com.example.example.repository.CouncilRoundRepository;
import com.example.example.repository.CouncilVoteRepository;
import com.example.example.repository.FactoryRepository;
import com.example.example.repository.ProductRepository;
import com.example.example.repository.UserSessionRepository;
import com.example.example.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * «Совет семей»: каждые 5 минут случайное событие, игроки голосуют, на чьей ферме оно сработает.
 *
 * Раунд проходит три стадии: ожидание (SCHEDULED) → голосование (VOTING) → итог (RESULT).
 * Состояние двигается лениво: любой запрос состояния закрывает просроченный раунд и заводит следующий.
 * Операции по одной партии выполняются под общим замком, поэтому раунд не задвоится.
 */
@Service
public class CouncilService {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String VOTING = "VOTING";
    public static final String RESULT = "RESULT";
    public static final String NONE = "NONE";

    /** Событие: код, смайл, название, описание, хорошее ли для цели. */
    public enum Ev {
        RAID("🚔", "Рейд полиции", "Полиция конфискует половину нелегального товара на складе.", false, 3),
        TAX("💸", "Налоговая проверка", "Цель платит 15% своих монет (но не меньше 10).", false, 3),
        STRIKE("✊", "Забастовка", "Одно из производств встаёт на 3 минуты.", false, 3),
        PESTS("🐛", "Нашествие вредителей", "Все грядки, которые ещё растут, созреют на 90 секунд позже.", false, 3),
        JACKPOT("🎰", "Джекпот", "Цель получает 150 монет. Выбирайте друга!", true, 2),
        HARVEST("🌾", "Урожайный год", "Все грядки, которые ещё растут, созреют вдвое быстрее: оставшееся время роста сокращается в 2 раза.", true, 2);

        public final String emoji, title, description;
        public final boolean good;
        public final int weight;

        Ev(String emoji, String title, String description, boolean good, int weight) {
            this.emoji = emoji; this.title = title; this.description = description;
            this.good = good; this.weight = weight;
        }
    }

    /** Сколько секунд между началами раундов. Меняется в application.properties: game.council.interval-seconds=300 */
    @Value("${game.council.interval-seconds:300}")
    private int intervalSeconds;
    /** Сколько секунд открыто голосование. */
    @Value("${game.council.vote-seconds:45}")
    private int voteSeconds;
    /** Сколько секунд после итога показывать результат. */
    @Value("${game.council.result-seconds:25}")
    private int resultSeconds;

    private final CouncilRoundRepository roundRepository;
    private final CouncilVoteRepository voteRepository;
    private final CouncilPlayerRepository playerRepository;
    private final UserSessionRepository userSessionRepository;
    private final ProductRepository productRepository;
    private final FactoryRepository factoryRepository;
    private final WalletRepository walletRepository;
    private final JsonMapper jsonMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate tx;

    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public CouncilService(CouncilRoundRepository roundRepository,
                          CouncilVoteRepository voteRepository,
                          CouncilPlayerRepository playerRepository,
                          UserSessionRepository userSessionRepository,
                          ProductRepository productRepository,
                          FactoryRepository factoryRepository,
                          WalletRepository walletRepository,
                          JsonMapper jsonMapper,
                          SimpMessagingTemplate messagingTemplate,
                          PlatformTransactionManager txManager) {
        this.roundRepository = roundRepository;
        this.voteRepository = voteRepository;
        this.playerRepository = playerRepository;
        this.userSessionRepository = userSessionRepository;
        this.productRepository = productRepository;
        this.factoryRepository = factoryRepository;
        this.walletRepository = walletRepository;
        this.jsonMapper = jsonMapper;
        this.messagingTemplate = messagingTemplate;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------- DTO ----------

    public record EventDto(String type, String emoji, String title, String description, boolean good) {}
    public record Candidate(Long userId, String nickName) {}
    public record TallyRow(Long userId, String nickName, int votes) {}
    public record ResultDto(Long targetUserId, String targetNick, String text, boolean skipped,
                            EventDto event, List<TallyRow> tally) {}

    /** Время — в миллисекундах Unix; serverNow нужен клиенту для поправки часов. */
    public record CouncilState(String phase, int roundNo, long serverNow,
                               Long startsAt, Long endsAt, EventDto event,
                               List<Candidate> candidates, Long myVote, ResultDto result) {}

    // ---------- API ----------

    public CouncilState state(String sessionCode, Long userId) {
        String code = sessionCode.toUpperCase();
        synchronized (lock(code)) {
            return tx.execute(st -> {
                Instant now = Instant.now();
                advance(code, now);
                return buildState(code, userId, now);
            });
        }
    }

    public CouncilState vote(String sessionCode, Long voterId, Long targetId) {
        String code = sessionCode.toUpperCase();
        synchronized (lock(code)) {
            return tx.execute(st -> {
                Instant now = Instant.now();
                advance(code, now);

                CouncilRound round = roundRepository.findTopBySessionCodeOrderByRoundNoDesc(code).orElse(null);
                if (round == null || round.isResolved()
                        || now.isBefore(round.getStartsAt()) || !now.isBefore(round.getEndsAt())) {
                    throw new CommonException("Voting is closed");
                }
                if (voterId.equals(targetId)) throw new CommonException("You cannot vote for yourself");

                Map<Long, UserSession> players = playersOf(code);
                if (!players.containsKey(voterId)) throw new CommonException("UserSession not found");
                if (!players.containsKey(targetId)) throw new CommonException("Player not found");

                CouncilVote v = voteRepository.findByRoundIdAndVoterId(round.getId(), voterId).orElseGet(() -> {
                    CouncilVote nv = new CouncilVote();
                    nv.setRoundId(round.getId());
                    nv.setVoterId(voterId);
                    return nv;
                });
                v.setTargetId(targetId);
                voteRepository.save(v);
                return buildState(code, voterId, now);
            });
        }
    }

    // ---------- движение раундов ----------

    private void advance(String code, Instant now) {
        CouncilRound latest = roundRepository.findTopBySessionCodeOrderByRoundNoDesc(code).orElse(null);
        if (latest == null) {
            // первое событие — через полный интервал после первого захода в партию
            createRound(code, 1, now.plusSeconds(intervalSeconds), null);
            return;
        }
        if (!latest.isResolved() && !now.isBefore(latest.getEndsAt())) {
            resolve(latest, now);
        }
    }

    private CouncilRound createRound(String code, int roundNo, Instant startsAt, String previousType) {
        CouncilRound r = new CouncilRound();
        r.setSessionCode(code);
        r.setRoundNo(roundNo);
        r.setEventType(pickEvent(previousType).name());
        r.setStartsAt(startsAt);
        r.setEndsAt(startsAt.plusSeconds(voteSeconds));
        r = roundRepository.save(r);
        publishChanged(code);
        return r;
    }

    private Ev pickEvent(String previousType) {
        List<Ev> pool = new ArrayList<>();
        for (Ev e : Ev.values()) {
            if (e.name().equals(previousType)) continue; // два одинаковых подряд не бывает
            for (int i = 0; i < e.weight; i++) pool.add(e);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private void resolve(CouncilRound round, Instant now) {
        String code = round.getSessionCode();
        Map<Long, UserSession> players = playersOf(code);
        Ev ev = Ev.valueOf(round.getEventType());

        // голоса только действующих игроков
        Map<Long, Integer> tally = new LinkedHashMap<>();
        for (CouncilVote v : voteRepository.findByRoundId(round.getId())) {
            if (!players.containsKey(v.getVoterId()) || !players.containsKey(v.getTargetId())) continue;
            tally.merge(v.getTargetId(), 1, Integer::sum);
        }

        round.setResolved(true);
        round.setResolvedAt(now);
        round.setTally(tally.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")));

        if (tally.isEmpty()) {
            round.setResultText("Никто не проголосовал — событие «" + ev.title + "» отменено.");
        } else {
            int max = Collections.max(tally.values());
            List<Long> leaders = tally.entrySet().stream()
                    .filter(e -> e.getValue() == max).map(Map.Entry::getKey).toList();
            Long targetId = leaders.get(random.nextInt(leaders.size())); // ничья — решает жребий
            UserSession target = players.get(targetId);
            String nick = nickOf(target);
            round.setTargetUserId(targetId);
            round.setTargetNick(nick);
            round.setResultText(apply(ev, code, target, nick) + (leaders.size() > 1 ? " (ничья — решил жребий)" : ""));
        }
        roundRepository.save(round);

        Instant next = round.getStartsAt().plusSeconds(intervalSeconds);
        if (next.isBefore(now.plusSeconds(30))) next = now.plusSeconds(30);
        createRound(code, round.getRoundNo() + 1, next, round.getEventType());
    }

    // ---------- эффекты ----------

    private String apply(Ev ev, String code, UserSession target, String nick) {
        Long uid = target.getUser().getUserId();
        Instant now = Instant.now();
        switch (ev) {
            case RAID: {
                Map<String, Product> products = productRepository.findAll().stream()
                        .collect(Collectors.toMap(Product::getCode, p -> p, (a, b) -> a));
                Storage storage = storageCopy(target);
                int taken = 0;
                for (Map.Entry<String, Integer> e : new ArrayList<>(storage.snapshot().entrySet())) {
                    Product p = products.get(e.getKey());
                    if (p == null || p.getLegality() != Product.Legality.ILLEGAL) continue;
                    int take = Math.max(1, e.getValue() / 2);
                    if (storage.remove(e.getKey(), take)) taken += take;
                }
                if (taken == 0) return "Полиция нагрянула к " + nick + ", но нелегального товара не нашла.";
                target.setStorage(storage);
                userSessionRepository.save(target);
                return "Полиция конфисковала у " + nick + " " + taken + " ед. нелегального товара!";
            }
            case TAX: {
                Wallet w = wallet(code, uid);
                long take = Math.min(w.getCoins(), Math.max(10, w.getCoins() * 15 / 100));
                w.setCoins(w.getCoins() - take);
                walletRepository.save(w);
                publishMarket(code);
                return nick + " заплатил налог: −" + take + " 🪙";
            }
            case JACKPOT: {
                Wallet w = wallet(code, uid);
                w.setCoins(w.getCoins() + 150);
                walletRepository.save(w);
                publishMarket(code);
                return nick + " сорвал джекпот: +150 🪙";
            }
            case STRIKE: {
                Board board = boardCopy(target);
                if (board == null) return "У " + nick + " нет зданий — бастовать некому.";
                // бастовать может любое производственное здание на ферме (склад и торговец — нет)
                List<PlacedObject> buildings = new ArrayList<>();
                for (PlacedObject o : board.getObjects()) {
                    if (o.getKind() != PlacedObject.Kind.BUILDING) continue;
                    String b = o.getBuilding();
                    if ("warehouse".equals(b) || "trader".equals(b)) continue;
                    buildings.add(o);
                }
                if (buildings.isEmpty()) return "У " + nick + " нет производственных зданий — бастовать некому.";
                PlacedObject o = buildings.get(random.nextInt(buildings.size()));
                final long delay = 180;
                boolean working = false;
                if (o.getProductionReadyAt() != null && o.getProductionReadyAt().isAfter(now)) {
                    o.setProductionReadyAt(o.getProductionReadyAt().plusSeconds(delay));
                    working = true;
                }
                for (PlacedObject.QueuedJob j : o.getQueue()) {
                    if (j.getReadyAt() == null || !j.getReadyAt().isAfter(now)) continue;
                    j.setReadyAt(j.getReadyAt().plusSeconds(delay));
                    if (j.getStartedAt() != null && j.getStartedAt().isAfter(now)) {
                        j.setStartedAt(j.getStartedAt().plusSeconds(delay));
                    }
                    working = true;
                }
                // пока идёт забастовка, новое производство в здании не запустить (даже если оно простаивало)
                Instant until = (o.getBlockedUntil() != null && o.getBlockedUntil().isAfter(now)
                        ? o.getBlockedUntil() : now).plusSeconds(delay);
                o.setBlockedUntil(until);
                saveBoard(target, board);
                String what = factoryRepository.findByCode(o.getBuilding())
                        .map(f -> f.getTitle()).orElse(o.getBuilding());
                return "Забастовка на ферме " + nick + ": «" + what + "» остановлено на 3 минуты"
                        + (working ? " (текущее производство отложено)." : " (здание простаивало — запустить производство нельзя).");
            }
            case PESTS:
            case HARVEST: {
                Board board = boardCopy(target);
                int n = 0;
                if (board != null) {
                    for (PlacedObject o : board.getObjects()) {
                        if (o.getKind() != PlacedObject.Kind.CROP) continue;
                        Instant ready = o.getReadyAt();
                        if (ready == null || !ready.isAfter(now)) continue;
                        if (ev == Ev.PESTS) {
                            o.setReadyAt(ready.plusSeconds(90));
                        } else {
                            long left = ready.getEpochSecond() - now.getEpochSecond();
                            o.setReadyAt(now.plusSeconds(Math.max(1, left / 2)));
                        }
                        n++;
                    }
                }
                if (n == 0) {
                    return "У " + nick + " сейчас ничего не растёт — «" + ev.title + "» прошёл мимо.";
                }
                saveBoard(target, board);
                return ev == Ev.PESTS
                        ? "Вредители напали на " + n + " грядок " + nick + ": рост замедлился на 90 секунд."
                        : "Урожайный год у " + nick + ": " + n + " грядок поспеют вдвое быстрее!";
            }
            default:
                return "";
        }
    }

    // ---------- состояние для клиента ----------

    private CouncilState buildState(String code, Long userId, Instant now) {
        Map<Long, UserSession> players = playersOf(code);
        long nowMs = now.toEpochMilli();
        if (players.size() < 2 || !players.containsKey(userId)) {
            return new CouncilState(NONE, 0, nowMs, null, null, null, List.of(), null, null);
        }
        CouncilRound latest = roundRepository.findTopBySessionCodeOrderByRoundNoDesc(code).orElse(null);
        if (latest == null) {
            return new CouncilState(NONE, 0, nowMs, null, null, null, List.of(), null, null);
        }

        List<Candidate> candidates = players.entrySet().stream()
                .filter(e -> !e.getKey().equals(userId))
                .map(e -> new Candidate(e.getKey(), nickOf(e.getValue())))
                .sorted(Comparator.comparing(Candidate::nickName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        // идёт голосование
        if (!latest.isResolved() && !now.isBefore(latest.getStartsAt())) {
            Long myVote = voteRepository.findByRoundIdAndVoterId(latest.getId(), userId)
                    .map(CouncilVote::getTargetId).orElse(null);
            return new CouncilState(VOTING, latest.getRoundNo(), nowMs,
                    latest.getStartsAt().toEpochMilli(), latest.getEndsAt().toEpochMilli(),
                    eventDto(latest.getEventType()), candidates, myVote, null);
        }

        // недавний итог
        CouncilRound last = latest.isResolved() ? latest
                : roundRepository.findTopBySessionCodeAndResolvedTrueOrderByRoundNoDesc(code).orElse(null);
        if (last != null && last.getResolvedAt() != null
                && now.isBefore(last.getResolvedAt().plusSeconds(resultSeconds))) {
            return new CouncilState(RESULT, latest.getRoundNo(), nowMs,
                    latest.getStartsAt().toEpochMilli(), latest.getEndsAt().toEpochMilli(),
                    null, candidates, null, resultDto(last, players));
        }

        // ждём следующее голосование (событие пока скрыто)
        return new CouncilState(SCHEDULED, latest.getRoundNo(), nowMs,
                latest.getStartsAt().toEpochMilli(), latest.getEndsAt().toEpochMilli(),
                null, candidates, null, null);
    }

    private ResultDto resultDto(CouncilRound r, Map<Long, UserSession> players) {
        List<TallyRow> rows = new ArrayList<>();
        if (r.getTally() != null && !r.getTally().isBlank()) {
            for (String part : r.getTally().split(",")) {
                String[] kv = part.split(":");
                Long id = Long.valueOf(kv[0]);
                UserSession us = players.get(id);
                rows.add(new TallyRow(id, us == null ? "?" : nickOf(us), Integer.parseInt(kv[1])));
            }
            rows.sort(Comparator.comparingInt(TallyRow::votes).reversed());
        }
        return new ResultDto(r.getTargetUserId(), r.getTargetNick(), r.getResultText(),
                r.getTargetUserId() == null, eventDto(r.getEventType()), rows);
    }

    private EventDto eventDto(String type) {
        Ev e = Ev.valueOf(type);
        return new EventDto(e.name(), e.emoji, e.title, e.description, e.good);
    }

    // ---------- вспомогательное ----------

    private Object lock(String code) {
        return locks.computeIfAbsent(code, k -> new Object());
    }

    private Map<Long, UserSession> playersOf(String code) {
        Map<Long, UserSession> map = new LinkedHashMap<>();
        for (UserSession us : playerRepository.findAllBySessionCode(code)) {
            map.put(us.getUser().getUserId(), us);
        }
        return map;
    }

    private String nickOf(UserSession us) {
        String nick = us.getUser().getNickName();
        return nick == null || nick.isBlank() ? "Игрок" : nick;
    }

    private Wallet wallet(String code, Long userId) {
        return walletRepository.findForUpdate(code, userId).orElseGet(() -> {
            Wallet w = new Wallet();
            w.setSessionCode(code);
            w.setUserId(userId);
            return walletRepository.save(w);
        });
    }

    private Storage storageCopy(UserSession us) {
        Storage s = us.getStorage();
        return s == null ? new Storage() : jsonMapper.convertValue(s, Storage.class);
    }

    private Board boardCopy(UserSession us) {
        Board b = us.getBoard();
        return b == null ? null : jsonMapper.convertValue(b, Board.class);
    }

    private void saveBoard(UserSession us, Board board) {
        us.setBoard(board);
        userSessionRepository.save(us);
        String destination = "/topic/board." + us.getSession().getSessionCode() + "." + us.getUser().getUserId();
        Board snapshot = jsonMapper.convertValue(board, Board.class);
        afterCommit(() -> messagingTemplate.convertAndSend(destination, snapshot));
    }

    private void publishChanged(String code) {
        Object payload = Map.<String, String>of("type", "changed");
        afterCommit(() -> messagingTemplate.convertAndSend("/topic/council." + code, payload));
    }

    private void publishMarket(String code) {
        Object payload = Map.<String, String>of("type", "changed");
        afterCommit(() -> messagingTemplate.convertAndSend("/topic/market." + code, payload));
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { r.run(); }
            });
        } else {
            r.run();
        }
    }
}