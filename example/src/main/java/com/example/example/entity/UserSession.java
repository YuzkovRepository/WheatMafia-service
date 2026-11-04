package com.example.example.entity;

import com.example.example.entity.board.Board;
import com.example.example.entity.board.Storage;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_session")
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userSessionId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id")
    private Session session;

    @Min(0)
    private int balance;

    @Min(0)
    private int reputation;

    @Column(name = "is_ready", nullable = false)
    private boolean isReady = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "board_json", nullable = true, columnDefinition = "jsonb")
    private Board board;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "storage_json", nullable = true, columnDefinition = "jsonb")
    private Storage storage;

    /** Регион-роль игрока в этой сессии: ITALY, JAPAN, EGYPT, BRAZIL */
    @Column(name = "region", length = 20)
    private String region;

    /** Открытые исследования: список code из таблицы research (в рамках своего region). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unlocked_research", columnDefinition = "jsonb")
    private Set<String> unlockedResearch = new HashSet<>();

    /** Открытые рецепты: список ключей "factoryCode:outputCode". */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unlocked_recipes", columnDefinition = "jsonb")
    private Set<String> unlockedRecipes = new HashSet<>();

    @OneToMany(mappedBy = "userSession", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<Market> markets = new HashSet<>();

    @PrePersist
    public void prePersist() {
        balance = 0;
        reputation = 0;
        if (unlockedResearch == null) unlockedResearch = new HashSet<>();
        if (unlockedRecipes == null) unlockedRecipes = new HashSet<>();
    }
}