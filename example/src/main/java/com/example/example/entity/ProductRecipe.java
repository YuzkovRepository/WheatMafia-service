package com.example.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_recipes")
@Getter
@Setter
@NoArgsConstructor
public class ProductRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "factory_code", nullable = false)
    private String factoryCode;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "output_product_id", nullable = false)
    private Product output;

    @Column(name = "output_qty", nullable = false)
    private int outputQty = 1;

    @Column(name = "production_time", nullable = false)
    private int productionTime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_recipe_inputs",
            joinColumns = @JoinColumn(name = "recipe_id"))
    private List<RecipeInput> inputs = new ArrayList<>();

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipeInput {
        @Column(name = "input_product_code", nullable = false)
        private String code;

        @Column(name = "qty", nullable = false)
        private int qty;
    }
}