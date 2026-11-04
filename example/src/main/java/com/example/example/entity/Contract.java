package com.example.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Контракт-обмен: создатель отдаёт набор товаров (offer) и хочет получить другой набор (request).
 * Отдаваемые товары списываются со склада создателя при создании (эскроу) и
 * возвращаются при отмене. Принять контракт может любой другой игрок партии.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "contracts")
public class Contract {

    public enum Status { ACTIVE, DONE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32, nullable = false)
    private String sessionCode;

    private Long creatorId;

    @Column(length = 60)
    private String creatorNick;

    /** Что создатель отдаёт. */
    @JdbcTypeCode(value = SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<DealItem> offer = new ArrayList<>();

    /** Что создатель хочет получить взамен. */
    @JdbcTypeCode(value = SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<DealItem> request = new ArrayList<>();

    @Column(length = 100)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Status status = Status.ACTIVE;

    private Long takerId;

    @Column(length = 60)
    private String takerNick;

    private Instant createdAt = Instant.now();

    private Instant closedAt;
}