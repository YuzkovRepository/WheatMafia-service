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
@Table(name = "factories")
@Getter
@Setter
@NoArgsConstructor
public class Factory {

    public enum ProductionKind { BUILDING, CROP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductionKind kind;

    private String icon;
    private int width;
    private int height;

    private String region;

    /** Код исследования, открывающего здание. null — доступно сразу. */
    @Column(name = "required_research")
    private String requiredResearch;
}