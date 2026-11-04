package com.example.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Суперзаказ: игрок хочет купить quantity штук продукта по price монет за штуку.
 * Монеты (quantity * price) резервируются с кошелька при создании; игроки могут
 * выполнять заказ частями. Нераспределённый остаток возвращается при отмене.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "buy_orders")
public class BuyOrder {

    public enum Status { ACTIVE, DONE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32, nullable = false)
    private String sessionCode;

    private Long buyerId;

    @Column(length = 60)
    private String buyerNick;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Сколько всего хотел купить. */
    private int quantity;

    /** Сколько уже куплено (выполнено другими игроками). */
    private int filled = 0;

    /** Цена за одну штуку. */
    private int price;

    @Column(length = 100)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Status status = Status.ACTIVE;

    private Instant createdAt = Instant.now();

    private Instant closedAt;

    public int remaining() {
        return quantity - filled;
    }
}
