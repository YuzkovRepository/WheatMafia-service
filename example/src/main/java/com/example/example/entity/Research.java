package com.example.example.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "researches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"region", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Research {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int stage;

    /** Фабрика, открываемая/используемая исследованием. */
    @Column(name = "factory_code")
    private String factoryCode;

    /** Продукт, открываемый исследованием. null — если research открывает только здание. */
    @Column(name = "output_code")
    private String outputCode;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "research_costs",
            joinColumns = @JoinColumn(name = "research_id"))
    private List<Cost> cost = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "research_requires",
            joinColumns = @JoinColumn(name = "research_id"))
    @Column(name = "required_code")
    private List<String> requires = new ArrayList<>();

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cost {
        @Column(name = "cost_code", nullable = false)
        private String code;

        @Column(name = "qty", nullable = false)
        private int qty;
    }
}