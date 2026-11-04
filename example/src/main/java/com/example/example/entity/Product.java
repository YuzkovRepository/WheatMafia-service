package com.example.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    public enum ProductKind { MATERIAL, GOODS, CROP }
    public enum Legality { LEGAL, ILLEGAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    private String icon;

    @Column(name = "production_time")
    private int productionTime;

    private int score;

    @Column(name = "base_score")
    private int baseScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Legality legality;
}