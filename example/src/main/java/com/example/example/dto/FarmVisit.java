package com.example.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Один визит игрока на чужую ферму. Лимит визитов считается по этим строкам:
 * удачный визит (FOUND — скупщик пойман) в лимит не засчитывается.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "farm_visits",
        indexes = @Index(name = "idx_farm_visits_visitor", columnList = "session_code, visitor_id"))
public class FarmVisit {

    public static final String ACTIVE = "ACTIVE";
    /** время вышло или игрок ушёл, никого не поймав */
    public static final String MISSED = "MISSED";
    public static final String FOUND = "FOUND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", length = 32, nullable = false)
    private String sessionCode;

    @Column(name = "visitor_id", nullable = false)
    private Long visitorId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(length = 16, nullable = false)
    private String status = ACTIVE;

    @Column(name = "bonus_coins", nullable = false)
    private long bonusCoins;
}