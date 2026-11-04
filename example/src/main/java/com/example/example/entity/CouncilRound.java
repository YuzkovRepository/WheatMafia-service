package com.example.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Один раунд «Совета»: случайное событие + голосование, на кого оно подействует.
 * Раунды создаются и закрываются лениво (при обращении игроков), отдельный планировщик не нужен.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "council_rounds",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_code", "round_no"}))
public class CouncilRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", length = 32, nullable = false)
    private String sessionCode;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    /** Код события (RAID, TAX, STRIKE, PESTS, JACKPOT, HARVEST). */
    @Column(name = "event_type", length = 32, nullable = false)
    private String eventType;

    /** Когда открывается голосование. */
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    /** Когда голосование закрывается. */
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean resolved;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "target_nick", length = 100)
    private String targetNick;

    @Column(name = "result_text", length = 500)
    private String resultText;

    /** «userId:голосов,userId:голосов» — итоговая таблица. */
    @Column(length = 1000)
    private String tally;
}