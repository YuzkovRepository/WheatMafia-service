package com.example.example.dto;

import java.util.List;

public record ResearchDetailsDto(
        String region,
        String code,
        String title,
        int stage,
        String requiredBuildingCode,
        String requiredBuildingTitle,
        String outputCode,
        String outputTitle,
        List<CostDto> cost,
        List<String> requires,
        List<RecipePreviewDto> unlocksRecipes
) {
    public record CostDto(String code, String title, int qty) {}

    public record RecipePreviewDto(
            String factoryCode,
            String factoryTitle,
            String outputCode,
            String outputTitle,
            int outputQty,
            int productionTime,
            List<IngredientDto> inputs
    ) {}

    public record IngredientDto(String code, String title, int qty) {}
}
