package com.example.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Одно блюдо, сданное депутату. slot: "S3", "S4"... или "GRAND". */
@Entity
@Table(name = "deputy_deliveries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_code", "user_id", "slot"}))
@Getter
@Setter
@NoArgsConstructor
public class DeputyDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", nullable = false, length = 16)
    private String sessionCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String slot;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt = Instant.now();
}