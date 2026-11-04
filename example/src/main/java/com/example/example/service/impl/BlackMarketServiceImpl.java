package com.example.example.service.impl;

import com.example.example.dto.BlackMarketDto;
import com.example.example.entity.BlackMarketVisit;
import com.example.example.entity.Product;
import com.example.example.entity.Session;
import com.example.example.entity.UserSession;
import com.example.example.entity.Wallet;
import com.example.example.entity.board.Storage;
import com.example.example.exception.CommonException;
import com.example.example.repository.BlackMarketVisitRepository;
import com.example.example.repository.ProductRepository;
import com.example.example.repository.SessionRepository;
import com.example.example.repository.UserSessionRepository;
import com.example.example.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Теневой скупщик: по вызову едет к ферме, покупает ВЕСЬ нелегальный товар любой страны по высокой цене.
 * Фазы считаются по времени (ARRIVING → PRESENT → LEFT), планировщик не нужен.
 */
@Service
@RequiredArgsConstructor
public class BlackMarketServiceImpl {

    /** Сколько скупщик едет до фермы. */
    private static final long ARRIVE_SECONDS = 20;
    /** Сколько он стоит на ферме (окно продажи и «заметности»). */
    private static final long STAY_SECONDS = 90;
    /** Пауза после отъезда, прежде чем можно вызвать снова. */
    private static final long COOLDOWN_SECONDS = 120;
    /** Последние секунды визита скупщик уезжает (анимация на клиенте). */
    private static final long DEPART_SECONDS = 6;

    /** Цена за единицу = очки продукта × BASE_MULT × pricePct / 100. */
    private static final int BASE_MULT = 8;
    private static final int PRICE_MIN_PCT = 90;
    private static final int PRICE_MAX_PCT = 125;

    private final BlackMarketVisitRepository visitRepository;
    private final UserSessionRepository userSessionRepository;
    private final SessionRepository sessionRepository;
    private final ProductRepository productRepository;
    private final WalletRepository walletRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;

    // ---------- чтение ----------

    @Transactional(readOnly = true)
    public BlackMarketDto.State state(String sessionCode, Long userId) {
        String code = sessionCode.toUpperCase();
        return buildState(code, findUserSession(code, userId));
    }

    // ---------- вызвать ----------

    @Transactional
    public BlackMarketDto.State summon(String sessionCode, Long userId) {
        String code = sessionCode.toUpperCase();
        UserSession us = findUserSession(code, userId);

        if (us.getSession().getStatus() == Session.Status.COMPLETED) {
            throw new CommonException("Игра уже завершена");
        }

        Instant now = Instant.now();
        BlackMarketVisit last = visitRepository
                .findFirstBySessionCodeAndUserIdOrderByIdDesc(code, userId).orElse(null);
        if (last != null) {
            if (now.isBefore(last.getLeavesAt())) {
                throw new CommonException("Скупщик уже у вас");
            }
            if (now.isBefore(last.getLeavesAt().plusSeconds(COOLDOWN_SECONDS))) {
                throw new CommonException("Скупщик отдыхает, подождите немного");
            }
        }
        if (quote(us, products(), 100).isEmpty()) {
            throw new CommonException("На складе нет нелегального товара");
        }

        BlackMarketVisit v = new BlackMarketVisit();
        v.setSessionCode(code);
        v.setUserId(userId);
        v.setSummonedAt(now);
        v.setArrivesAt(now.plusSeconds(ARRIVE_SECONDS));
        v.setLeavesAt(now.plusSeconds(ARRIVE_SECONDS + STAY_SECONDS));
        v.setPricePct(ThreadLocalRandom.current().nextInt(PRICE_MIN_PCT, PRICE_MAX_PCT + 1));
        visitRepository.save(v);

        BlackMarketDto.State state = buildState(code, us);
        publish(code, new BlackMarketDto.Event("SUMMONED", userId, us.getUser().getNickName(),
                state.active(), now.toEpochMilli()));
        return state;
    }

    // ---------- отпустить раньше срока ----------

    @Transactional
    public BlackMarketDto.State dismiss(String sessionCode, Long userId) {
        String code = sessionCode.toUpperCase();
        UserSession us = findUserSession(code, userId);
        Instant now = Instant.now();

        BlackMarketVisit v = visitRepository
                .findFirstBySessionCodeAndUserIdOrderByIdDesc(code, userId).orElse(null);
        if (v != null && now.isBefore(v.getLeavesAt())) {
            Instant out = now.plusSeconds(DEPART_SECONDS);
            if (out.isBefore(v.getLeavesAt())) v.setLeavesAt(out);
            if (v.getArrivesAt().isAfter(out)) v.setArrivesAt(out);
            visitRepository.save(v);
        }

        BlackMarketDto.State state = buildState(code, us);
        publish(code, new BlackMarketDto.Event("LEFT", userId, us.getUser().getNickName(),
                state.active(), now.toEpochMilli()));
        return state;
    }

    // ---------- продать ----------

    @Transactional
    public BlackMarketDto.SellResult sell(String sessionCode, Long userId, BlackMarketDto.SellRequest req) {
        String code = sessionCode.toUpperCase();
        UserSession us = findUserSession(code, userId);
        Instant now = Instant.now();

        BlackMarketVisit visit = visitRepository
                .findFirstBySessionCodeAndUserIdOrderByIdDesc(code, userId)
                .orElseThrow(() -> new CommonException("Сначала вызовите скупщика"));
        if (!now.isBefore(visit.getLeavesAt())) throw new CommonException("Скупщик уже уехал");
        if (now.isBefore(visit.getArrivesAt())) throw new CommonException("Скупщик ещё в пути");

        String only = req == null ? null : req.productCode();
        Integer limit = req == null ? null : req.quantity();
        if (limit != null && limit < 1) throw new CommonException("Некорректное количество");

        Map<String, Product> products = products();
        Storage storage = us.getStorage() == null
                ? new Storage()
                : jsonMapper.convertValue(us.getStorage(), Storage.class);

        long earned = 0;
        int units = 0;
        for (Map.Entry<String, Integer> e : new HashMap<>(storage.snapshot()).entrySet()) {
            Product p = products.get(e.getKey());
            if (p == null || p.getLegality() != Product.Legality.ILLEGAL) continue;
            if (only != null && !only.equals(e.getKey())) continue;

            int qty = e.getValue();
            if (only != null && limit != null) qty = Math.min(qty, limit);
            if (qty < 1) continue;
            if (!storage.remove(e.getKey(), qty)) continue;

            earned += unitPrice(p, visit.getPricePct()) * qty;
            units += qty;
        }
        if (units == 0) throw new CommonException("Нечего продавать");

        us.setStorage(storage);
        userSessionRepository.save(us);

        Wallet wallet = walletRepository.findForUpdate(code, userId).orElseGet(() -> {
            Wallet w = new Wallet();
            w.setSessionCode(code);
            w.setUserId(userId);
            return walletRepository.save(w);
        });
        wallet.setCoins(wallet.getCoins() + earned);
        walletRepository.save(wallet);

        visit.setEarned(visit.getEarned() + earned);
        visit.setSoldUnits(visit.getSoldUnits() + units);
        visitRepository.save(visit);

        return new BlackMarketDto.SellResult(wallet.getCoins(), earned, units, buildState(code, us));
    }

    // ---------- расчёты ----------

    private BlackMarketDto.State buildState(String code, UserSession us) {
        Instant now = Instant.now();
        Long userId = us.getUser().getUserId();

        BlackMarketVisit last = visitRepository
                .findFirstBySessionCodeAndUserIdOrderByIdDesc(code, userId).orElse(null);
        boolean active = last != null && now.isBefore(last.getLeavesAt());

        BlackMarketDto.Visit mine = last == null ? null : new BlackMarketDto.Visit(
                last.getId(),
                last.getSummonedAt().toEpochMilli(),
                last.getArrivesAt().toEpochMilli(),
                last.getLeavesAt().toEpochMilli(),
                last.getPricePct(), last.getEarned(), last.getSoldUnits(),
                phase(last, now));
        long cooldownUntil = last == null ? 0
                : last.getLeavesAt().plusSeconds(COOLDOWN_SECONDS).toEpochMilli();

        List<BlackMarketDto.QuoteLine> quote = quote(us, products(), active ? last.getPricePct() : 100);
        long total = quote.stream().mapToLong(BlackMarketDto.QuoteLine::total).sum();

        return new BlackMarketDto.State(now.toEpochMilli(), mine, cooldownUntil,
                quote, total, publicActive(code, now));
    }

    /** Что скупщик купит со склада: только нелегал, любой страны. */
    private List<BlackMarketDto.QuoteLine> quote(UserSession us, Map<String, Product> products, int pct) {
        List<BlackMarketDto.QuoteLine> out = new ArrayList<>();
        if (us.getStorage() == null) return out;
        for (Map.Entry<String, Integer> e : new TreeMap<>(us.getStorage().snapshot()).entrySet()) {
            Product p = products.get(e.getKey());
            if (p == null || p.getLegality() != Product.Legality.ILLEGAL || e.getValue() <= 0) continue;
            long unit = unitPrice(p, pct);
            out.add(new BlackMarketDto.QuoteLine(p.getCode(), p.getTitle(), e.getValue(), unit, unit * e.getValue()));
        }
        return out;
    }

    private long unitPrice(Product p, int pct) {
        double base = (double) Math.max(1, p.getScore()) * BASE_MULT * pct / 100.0;
        return Math.max(1L, Math.round(base));
    }

    private List<BlackMarketDto.PublicVisit> publicActive(String code, Instant now) {
        Map<Long, UserSession> byUser = new HashMap<>();
        sessionRepository.findBySessionCode(code).ifPresent(s -> {
            for (UserSession u : s.getUserSessions()) byUser.put(u.getUser().getUserId(), u);
        });
        return visitRepository.findBySessionCodeAndLeavesAtAfter(code, now).stream()
                .map(v -> {
                    UserSession u = byUser.get(v.getUserId());
                    return new BlackMarketDto.PublicVisit(
                            v.getUserId(),
                            u != null ? u.getUser().getNickName() : "Игрок",
                            u != null ? u.getRegion() : null,
                            v.getArrivesAt().toEpochMilli(),
                            v.getLeavesAt().toEpochMilli(),
                            phase(v, now));
                })
                .toList();
    }

    private String phase(BlackMarketVisit v, Instant now) {
        if (!now.isBefore(v.getLeavesAt())) return "LEFT";
        return now.isBefore(v.getArrivesAt()) ? "ARRIVING" : "PRESENT";
    }

    private Map<String, Product> products() {
        Map<String, Product> m = new HashMap<>();
        for (Product p : productRepository.findAll()) m.put(p.getCode(), p);
        return m;
    }

    private UserSession findUserSession(String code, Long userId) {
        return userSessionRepository.findBySessionCodeAndUserId(code, userId)
                .orElseThrow(() -> new CommonException("UserSession not found"));
    }

    // ---------- рассылка всем игрокам партии ----------

    private void publish(String sessionCode, BlackMarketDto.Event event) {
        String destination = "/topic/blackmarket." + sessionCode;
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