package com.example.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Один визит теневого скупщика на ферму игрока (журнал для будущих санкций). */
@Entity
@Table(name = "black_market_visits",
        indexes = @Index(name = "idx_bm_session_user", columnList = "session_code,user_id"))
@Getter
@Setter
@NoArgsConstructor
public class BlackMarketVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", nullable = false, length = 16)
    private String sessionCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "summoned_at", nullable = false)
    private Instant summonedAt;

    /** Когда скупщик доехал до фермы. */
    @Column(name = "arrives_at", nullable = false)
    private Instant arrivesAt;

    /** Когда он уехал. До этого момента скупщика видно всем. */
    @Column(name = "leaves_at", nullable = false)
    private Instant leavesAt;

    /** Множитель цены на этот визит, %. */
    @Column(name = "price_pct", nullable = false)
    private int pricePct;

    @Column(nullable = false)
    private long earned;

    @Column(name = "sold_units", nullable = false)
    private int soldUnits;
}