package com.example.example.dto;

import java.time.Instant;
import java.util.List;

/** DTO контрактов и суперзаказов. */
public final class DealDto {

    private DealDto() {}

    public record Item(String code, int qty) {}

    // ----- контракты -----
    public record CreateContract(List<Item> offer, List<Item> request, String description) {}

    public record ContractView(Long id, Long creatorId, String creatorNick,
                               List<Item> offer, List<Item> request,
                               String description, Instant createdAt) {}

    // ----- суперзаказы -----
    public record CreateOrder(String productCode, int quantity, int price, String description) {}

    public record FulfillOrder(int quantity) {}

    /** remaining — сколько ещё нужно купить, quantity — сколько заказано всего. */
    public record OrderView(Long id, Long buyerId, String buyerNick,
                            String productCode, String productTitle,
                            int quantity, int remaining, int price,
                            String description, Instant createdAt) {}

    /** Ответ на любое действие: баланс игрока + все активные контракты и заказы партии. */
    public record Overview(long coins, List<ContractView> contracts, List<OrderView> orders) {}
}
