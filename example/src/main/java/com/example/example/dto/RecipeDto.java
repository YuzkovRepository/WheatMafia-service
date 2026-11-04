package com.example.example.dto;

import com.example.example.entity.ProductRecipe;

import java.util.List;

public record RecipeDto(
        Long id,
        String factoryCode,
        String outputCode,
        int outputQty,
        List<InputDto> inputs,
        int productionTime
) {
    public record InputDto(String code, int qty) {}

    public static RecipeDto from(ProductRecipe r) {
        return new RecipeDto(
                r.getId(),
                r.getFactoryCode(),
                r.getOutput().getCode(),
                r.getOutputQty(),
                r.getInputs().stream()
                        .map(i -> new InputDto(i.getCode(), i.getQty()))
                        .toList(),
                r.getProductionTime()
        );
    }
}
