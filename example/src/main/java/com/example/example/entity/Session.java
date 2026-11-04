package com.example.example.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sessions")
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sessionId;

    @Min(0)
    @Max(6)
    private int quantity_players;

    @Column(nullable = false, length = 4, unique = true)
    private String sessionCode;


    @Enumerated(EnumType.STRING)
    private Status status;

    @Min(1)
    @Max(8)
    private int round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    User winner;

    private int score_max;
    private LocalDateTime roundStartedAt;

    private int roundDurationSeconds;

    @PrePersist
    public void prePersist() {
        if (round == 0) round = 1;
        if (status == null) status = Status.PENDING;
        if (quantity_players == 0) quantity_players = 1;
        if (roundDurationSeconds == 0) roundDurationSeconds = 600;
    }

    public enum Status {
        PENDING,
        IN_PROCESS,
        COMPLETED
    }

    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<UserSession> userSessions = new HashSet<>();

}
