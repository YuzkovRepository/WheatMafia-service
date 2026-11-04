package com.example.example.service;

import java.time.Instant;
import java.util.Optional;

/**
 * Мост к ВАШЕМУ теневому скупщику (blackmarket): визиты на чужие фермы должны знать,
 * есть ли у игрока сейчас активный скупщик. Реализуйте в BlackMarketBuyerPresence.
 */
public interface BuyerPresence {

    /** Окно визита скупщика (время в миллисекундах Unix) — те же числа, что в BmVisit на клиенте. */
    record BuyerWindow(long summonedAtMs, long arrivesAtMs, long leavesAtMs) {}

    /**
     * Активный скупщик игрока на момент now: вызван и ещё не уехал (едет или стоит у фермы).
     * Если скупщика нет — Optional.empty().
     */
    Optional<BuyerWindow> activeBuyer(String sessionCode, Long userId, Instant now);

    /**
     * Можно ли разоблачить скупщика сейчас: в последние секунды визита (и после «отпустить»)
     * он уже уезжает, поэтому поймать его второй раз нельзя.
     */
    default boolean isCatchable(BuyerWindow w, Instant now) {
        return now.toEpochMilli() < w.leavesAtMs() - 6000;
    }

    /** Спугнуть скупщика после разоблачения (отпустить без продажи). По умолчанию ничего не делает. */
    default void scare(String sessionCode, Long userId) {}
}