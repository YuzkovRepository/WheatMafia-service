package com.example.example.dto;

import java.util.List;

public class BlackMarketDto {

    /** phase: ARRIVING (едет) / PRESENT (на месте) / LEFT (уехал). Время — epoch ms. */
    public record Visit(Long id, long summonedAtMs, long arrivesAtMs, long leavesAtMs,
                        int pricePct, long earned, int soldUnits, String phase) {}

    public record QuoteLine(String code, String title, int qty, long unitPrice, long total) {}

    public record PublicVisit(Long userId, String nickName, String region,
                              long arrivesAtMs, long leavesAtMs, String phase) {}

    /** mine — последний визит игрока; quote — что скупщик купит со склада; active — активные визиты в партии. */
    public record State(long serverNowMs, Visit mine, long cooldownUntilMs,
                        List<QuoteLine> quote, long quoteTotal, List<PublicVisit> active) {}

    /** productCode == null — продать весь нелегал; quantity == null — всё количество. */
    public record SellRequest(String productCode, Integer quantity) {}

    public record SellResult(long coins, long earned, int units, State state) {}

    /** type: SUMMONED / LEFT. Приходит всем игрокам партии. */
    public record Event(String type, Long userId, String nickName,
                        List<PublicVisit> active, long serverNowMs) {}
}