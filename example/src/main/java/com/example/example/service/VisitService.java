package com.example.example.service;

import com.example.example.entity.FarmVisit;
import com.example.example.entity.Product;
import com.example.example.entity.UserSession;
import com.example.example.entity.Wallet;
import com.example.example.entity.board.Board;
import com.example.example.entity.board.Storage;
import com.example.example.exception.CommonException;
import com.example.example.repository.CouncilPlayerRepository;
import com.example.example.repository.FarmVisitRepository;
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
 * Визиты на чужие фермы. У каждого игрока ограниченное число визитов на партию (по умолчанию 3).
 * Игрок сам выбирает, чью ферму навестить; на ней можно в течение ограниченного времени
 * заметить нелегального скупщика и нажать на него: цель получает санкции, визитёр — бонус
 * (и этот визит не тратится).
 *
 * Все операции по одной партии идут под общим замком — лимиты нельзя обойти быстрыми кликами.
 */
@Service
public class VisitService {

    /** Как у теневого скупщика: цена нелегала = score × 8 (BASE_MULT). */
    private static final int PRICE_MULTIPLIER = 8;

    @Value("${game.visits.max-per-game:3}")
    private int maxVisits;
    /** Сколько секунд длится визит. */
    @Value("${game.visits.visit-seconds:90}")
    private int visitSeconds;
    /** Пауза между визитами (от начала предыдущего). */
    @Value("${game.visits.cooldown-seconds:120}")
    private int cooldownSeconds;
    /** Штраф цели: % монет, но не меньше и не больше. */
    @Value("${game.visits.fine-percent:10}")
    private int finePercent;
    @Value("${game.visits.fine-min:25}")
    private long fineMin;
    @Value("${game.visits.fine-max:300}")
    private long fineMax;
    /** Какую долю нелегального товара на складе цели конфискуют. */
    @Value("${game.visits.confiscate-percent:50}")
    private int confiscatePercent;
    /** Базовый бонус визитёру; плюс половина штрафа и четверть стоимости конфискованного. */
    @Value("${game.visits.bonus-base:40}")
    private long bonusBase;

    private final FarmVisitRepository visitRepository;
    private final CouncilPlayerRepository playerRepository;
    private final UserSessionRepository userSessionRepository;
    private final ProductRepository productRepository;
    private final WalletRepository walletRepository;
    private final BuyerPresence presence;
    private final JsonMapper jsonMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate tx;

    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public VisitService(FarmVisitRepository visitRepository,
                        CouncilPlayerRepository playerRepository,
                        UserSessionRepository userSessionRepository,
                        ProductRepository productRepository,
                        WalletRepository walletRepository,
                        BuyerPresence presence,
                        JsonMapper jsonMapper,
                        SimpMessagingTemplate messagingTemplate,
                        PlatformTransactionManager txManager) {
        this.visitRepository = visitRepository;
        this.playerRepository = playerRepository;
        this.userSessionRepository = userSessionRepository;
        this.productRepository = productRepository;
        this.walletRepository = walletRepository;
        this.presence = presence;
        this.jsonMapper = jsonMapper;
        this.messagingTemplate = messagingTemplate;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------- DTO (время — миллисекунды Unix) ----------

    public record ActiveDto(Long visitId, String targetNick, String region, long endsAtMs) {}
    public record HistoryRow(String targetNick, String status, long bonusCoins, long startedAtMs) {}
    /** Чью ферму можно навестить; visits — сколько раз вы там уже были. */
    public record Candidate(Long userId, String nickName, String region, int visits) {}
    public record VisitState(long serverNowMs, int max, int remaining, long cooldownUntilMs,
                             ActiveDto active, List<HistoryRow> history, List<Candidate> candidates) {}

    public record BuyerDto(long summonedAtMs, long arrivesAtMs, long leavesAtMs) {}
    /** Чужая ферма «как есть»: только поле и скупщик, без склада и денег. */
    public record VisitView(Long visitId, String targetNick, String region, long endsAtMs, long serverNowMs,
                            Board board, BuyerDto buyer) {}

    public record ReportResult(boolean found, String message, long bonusCoins, long fine,
                               int confiscatedUnits, String targetNick, VisitState state) {}

    // ---------- API ----------

    public VisitState state(String sessionCode, Long userId) {
        String code = sessionCode.toUpperCase();
        synchronized (lock(code)) {
            return tx.execute(st -> {
                Instant now = Instant.now();
                expire(code, userId, now);
                return buildState(code, userId, now);
            });
        }
    }

    public VisitState start(String sessionCode, Long userId, Long targetId) {
        String code = sessionCode.toUpperCase();
        synchronized (lock(code)) {
            return tx.execute(st -> {
                Instant now = Instant.now();
                expire(code, userId, now);

                if (visitRepository.findFirstBySessionCodeAndVisitorIdAndStatus(code, userId, FarmVisit.ACTIVE).isPresent()) {
                    throw new CommonException("Visit already in progress");
                }
                VisitState cur = buildState(code, userId, now);
                if (cur.remaining() <= 0) throw new CommonException("No visits left");
                if (now.toEpochMilli() < cur.cooldownUntilMs()) throw new CommonException("Wait before the next visit");

                Map<Long, UserSession> players = playersOf(code);
                if (!players.containsKey(userId)) throw new CommonException("UserSession not found");
                if (targetId == null || targetId.equals(userId)) throw new CommonException("Choose another player");
                if (!players.containsKey(targetId)) throw new CommonException("Player not found");

                FarmVisit v = new FarmVisit();
                v.setSessionCode(code);
                v.setVisitorId(userId);
                v.setTargetId(targetId);
                v.setStartedAt(now);
                v.setEndsAt(now.plusSeconds(visitSeconds));
                visitRepository.save(v);
                return buildState(code, userId, now);
            });
        }
    }

    /** Что видит визитёр на чужой ферме (опрашивается клиентом, пока визит идёт). */
    public VisitView view(String sessionCode, Long userId) {
        String code = sessionCode.toUpperCase();
        synchronized (lock(code)) {
            return tx.execute(st -> {
                Instant now = Instant.now();
                expire(code, userId, now);
                FarmVisit v = visitRepository
                        .findFirstBySessionCodeAndVisitorIdAndStatus(code, userId, FarmVisit.ACTIVE)
                        .orElseThrow(() -> new CommonException("No active visit"));
                UserSession target = playersOf(code).get(v.getTargetId());
                if (target == null) throw new CommonException("Player not found");
                Board board = target.getBoard() == null ? new Board() : target.getBoard();
                BuyerDto buyer = presence.activeBuyer(code, v.getTargetId(), now)
                        .map(b -> new BuyerDto(b.summonedAtMs(), b.arrivesAtMs(), b.leavesAtMs()))
                        .orElse(null);
                return new VisitView(v.getId(), nickOf(target), regionOf(target), v.getEndsAt().toEpochMilli(),
                        now.toEpochMilli(), board, buyer);
            });
        }
    }

    public VisitState leave(String sessionCode, Long userId, Long visitId) {
        String code = sessionCode.toUpperCase();
        synchronized (lock(code)) {
            return tx.execute(st -> {
                Instant now = Instant.now();
                visitRepository.findById(visitId).ifPresent(v -> {
                    if (code.equals(v.getSessionCode()) && userId.equals(v.getVisitorId())
                            && FarmVisit.ACTIVE.equals(v.getStatus())) {
                        v.setStatus(FarmVisit.MISSED);
                        visitRepository.save(v);
                    }
                });
                return buildState(code, userId, now);
            });
        }
    }

    /** Клик по скупщику на чужой ферме. */
    public ReportResult report(String sessionCode, Long userId, Long visitId) {
        String code = sessionCode.toUpperCase();
        synchronized (lock(code)) {
            return tx.execute(st -> {
                Instant now = Instant.now();
                expire(code, userId, now);

                FarmVisit v = visitRepository.findById(visitId)
                        .filter(x -> code.equals(x.getSessionCode()) && userId.equals(x.getVisitorId()))
                        .orElseThrow(() -> new CommonException("Visit not found"));
                if (!FarmVisit.ACTIVE.equals(v.getStatus())) throw new CommonException("Visit is over");

                Optional<BuyerPresence.BuyerWindow> buyer = presence.activeBuyer(code, v.getTargetId(), now);
                if (buyer.isEmpty() || !presence.isCatchable(buyer.get(), now)) {
                    // скупщик успел уехать — визит не тратится, можно осмотреться дальше
                    return new ReportResult(false, "Скупщик уже уехал — вы опоздали.", 0, 0, 0, null,
                            buildState(code, userId, now));
                }

                Map<Long, UserSession> players = playersOf(code);
                UserSession target = players.get(v.getTargetId());
                UserSession visitor = players.get(userId);
                if (target == null || visitor == null) throw new CommonException("Player not found");

                // --- санкции цели ---
                // 1) конфискация части нелегального товара
                Map<String, Product> products = productRepository.findAll().stream()
                        .collect(Collectors.toMap(Product::getCode, p -> p, (a, b) -> a));
                Storage storage = target.getStorage() == null ? new Storage()
                        : jsonMapper.convertValue(target.getStorage(), Storage.class);
                int units = 0;
                long confiscatedValue = 0;
                for (Map.Entry<String, Integer> e : new ArrayList<>(storage.snapshot().entrySet())) {
                    Product p = products.get(e.getKey());
                    if (p == null || p.getLegality() != Product.Legality.ILLEGAL) continue;
                    int take = Math.max(1, e.getValue() * confiscatePercent / 100);
                    take = Math.min(take, e.getValue());
                    if (storage.remove(e.getKey(), take)) {
                        units += take;
                        confiscatedValue += (long) Math.max(1, p.getScore()) * PRICE_MULTIPLIER * take;
                    }
                }
                if (units > 0) {
                    target.setStorage(storage);
                    userSessionRepository.save(target);
                }

                // 2) штраф монетами; кошельки блокируем в порядке id, чтобы не было взаимной блокировки
                Long a = Math.min(userId, v.getTargetId()), b = Math.max(userId, v.getTargetId());
                Wallet wa = wallet(code, a), wb = wallet(code, b);
                Wallet wTarget = a.equals(v.getTargetId()) ? wa : wb;
                Wallet wVisitor = a.equals(userId) ? wa : wb;

                long fine = Math.min(wTarget.getCoins(),
                        Math.max(fineMin, Math.min(fineMax, wTarget.getCoins() * finePercent / 100)));
                long bonus = bonusBase + fine / 2 + confiscatedValue / 4;
                wTarget.setCoins(wTarget.getCoins() - fine);
                wVisitor.setCoins(wVisitor.getCoins() + bonus);
                walletRepository.save(wTarget);
                walletRepository.save(wVisitor);

                // 3) скупщик спугнут
                presence.scare(code, v.getTargetId());

                v.setStatus(FarmVisit.FOUND); // удачный визит не тратится
                v.setBonusCoins(bonus);
                visitRepository.save(v);

                String targetNick = nickOf(target);
                String msg = "Скупщик пойман! " + targetNick + " оштрафован на " + fine + " 🪙"
                        + (units > 0 ? ", конфисковано " + units + " ед. нелегала" : "")
                        + ". Ваш бонус: +" + bonus + " 🪙 (визит не потрачен).";

                Map<String, Object> ev = new HashMap<>();
                ev.put("type", "CAUGHT");
                ev.put("visitorId", userId);
                ev.put("visitorNick", nickOf(visitor));
                ev.put("targetUserId", v.getTargetId());
                ev.put("targetNick", targetNick);
                ev.put("fine", fine);
                ev.put("confiscatedUnits", units);
                // payload как Object: иначе convertAndSend(String, Map) неоднозначен (перегрузка с заголовками)
                Object visitPayload = ev;
                Object marketPayload = Map.<String, String>of("type", "changed");
                afterCommit(() -> {
                    messagingTemplate.convertAndSend("/topic/visits." + code, visitPayload);
                    messagingTemplate.convertAndSend("/topic/market." + code, marketPayload);
                });

                return new ReportResult(true, msg, bonus, fine, units, targetNick, buildState(code, userId, now));
            });
        }
    }

    // ---------- внутреннее ----------

    private void expire(String code, Long userId, Instant now) {
        visitRepository.findFirstBySessionCodeAndVisitorIdAndStatus(code, userId, FarmVisit.ACTIVE).ifPresent(v -> {
            if (!now.isBefore(v.getEndsAt())) {
                v.setStatus(FarmVisit.MISSED);
                visitRepository.save(v);
            }
        });
    }

    private VisitState buildState(String code, Long userId, Instant now) {
        List<FarmVisit> all = visitRepository.findBySessionCodeAndVisitorIdOrderByStartedAtDesc(code, userId);
        long used = all.stream().filter(v -> !FarmVisit.FOUND.equals(v.getStatus())).count();
        int remaining = (int) Math.max(0, maxVisits - used);
        long cooldownUntil = all.isEmpty() ? 0 : all.get(0).getStartedAt().plusSeconds(cooldownSeconds).toEpochMilli();

        Map<Long, UserSession> players = playersOf(code);
        ActiveDto active = all.stream().filter(v -> FarmVisit.ACTIVE.equals(v.getStatus())).findFirst()
                .map(v -> {
                    UserSession t = players.get(v.getTargetId());
                    return new ActiveDto(v.getId(), t == null ? "?" : nickOf(t), t == null ? null : regionOf(t),
                            v.getEndsAt().toEpochMilli());
                }).orElse(null);

        List<HistoryRow> history = all.stream().limit(5).map(v -> {
            UserSession t = players.get(v.getTargetId());
            return new HistoryRow(t == null ? "?" : nickOf(t), v.getStatus(), v.getBonusCoins(),
                    v.getStartedAt().toEpochMilli());
        }).toList();

        Map<Long, Integer> timesVisited = new HashMap<>();
        for (FarmVisit v : all) timesVisited.merge(v.getTargetId(), 1, Integer::sum);
        List<Candidate> candidates = players.entrySet().stream()
                .filter(e -> !e.getKey().equals(userId))
                .map(e -> new Candidate(e.getKey(), nickOf(e.getValue()), regionOf(e.getValue()),
                        timesVisited.getOrDefault(e.getKey(), 0)))
                .sorted(Comparator.comparing(Candidate::nickName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new VisitState(now.toEpochMilli(), maxVisits, remaining, cooldownUntil, active, history, candidates);
    }

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

    private String regionOf(UserSession us) {
        Object r = us.getRegion();
        return r == null ? null : r.toString();
    }

    private Wallet wallet(String code, Long userId) {
        return walletRepository.findForUpdate(code, userId).orElseGet(() -> {
            Wallet w = new Wallet();
            w.setSessionCode(code);
            w.setUserId(userId);
            return walletRepository.save(w);
        });
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