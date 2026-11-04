package com.example.example.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Лот на рынке торговца: игрок выставляет продукт/культуру со своего склада.
 * Цена — за одну штуку; весь лот покупается целиком.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "market_slots")
public class Market {

    public enum Status { ACTIVE, SOLD, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long slotId;

    /** Продавец (его UserSession). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "userSession_id")
    private UserSession userSession;

    /** Код партии — лоты видны только игрокам своей партии. */
    @Column(length = 32)
    private String sessionCode;

    private Long sellerId;

    /** Ник продавца на момент выставления. */
    @Column(length = 60)
    private String sellerNick;

    /** Что продаём (раньше здесь была Factory — продаются продукты, а не здания). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;

    @Min(1)
    @Max(100)
    private int quantity;

    /** Цена за одну штуку. */
    @Min(1)
    private int price;

    @Column(nullable = true, length = 100)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Status status = Status.ACTIVE;

    private Instant createdAt = Instant.now();

    private Instant closedAt;
}