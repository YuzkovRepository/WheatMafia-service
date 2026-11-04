package com.example.example.dto;


/** DTO скупщика (торговца-NPC). */
public final class TraderDto {

    private TraderDto() {}

    /** Цена, по которой скупщик покупает одну единицу продукта. */
    public record Price(String code, int price) {}

    public record SellRequest(String productCode, int quantity) {}

    /** coins — новый баланс игрока, earned — сколько заработано за эту продажу. */
    public record SellResult(long coins, long earned) {}
}
