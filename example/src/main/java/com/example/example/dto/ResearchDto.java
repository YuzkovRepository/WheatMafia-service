package com.example.example.dto;

import java.util.List;
import java.util.Map;

public final class ResearchDto {

    private ResearchDto() {}

    public record Tree(
            String region,
            List<Node> nodes,
            Map<String, Integer> storage
    ) {}

    public record Node(
            String code,
            String title,
            int stage,
            String factoryCode,
            String factoryTitle,      // ← человекочитаемое здание
            String outputCode,
            String outputTitle,       // ← человекочитаемый продукт
            List<Cost> cost,
            List<String> requires,
            boolean unlocked,
            boolean available,
            List<RecipePreview> unlocksRecipes   // ← что и из чего делается
    ) {}

    public record Cost(String code, String title, int qty) {}

    public record RecipePreview(
            String factoryCode,
            String factoryTitle,
            String outputCode,
            String outputTitle,
            int outputQty,
            int productionTime,
            List<Ingredient> inputs
    ) {}

    public record Ingredient(String code, String title, int qty) {}
}