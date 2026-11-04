package com.example.example.dto;

import java.time.Instant;

public record LotDto(Long slotId, Long sellerId, String sellerNick,
                     String productCode, String productTitle, String productKind,
                     int quantity, int price, String description, Instant createdAt) {}
