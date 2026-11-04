package com.example.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Кошелёк игрока в партии. Монеты хранятся отдельно от склада (Storage),
 * на склад идут только продукты и культуры.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "wallets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_code", "user_id"}))
public class Wallet {

    /** Стартовые монеты нового кошелька. */
    public static final long START_COINS = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", length = 32, nullable = false)
    private String sessionCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long coins = START_COINS;
}