package com.example.example.dto;

import com.example.example.entity.Product;

public record ProductDto(
        Long id,
        String code,
        String title,
        String icon,
        int productionTime,
        int score,
        int baseScore,
        String kind,
        String legality
) {
    public static ProductDto from(Product p) {
        return new ProductDto(
                p.getId(), p.getCode(), p.getTitle(), p.getIcon(),
                p.getProductionTime(), p.getScore(), p.getBaseScore(),
                p.getKind().name(), p.getLegality().name()
        );
    }
}
