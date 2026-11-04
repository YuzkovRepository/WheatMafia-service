package com.example.example.repository;

import com.example.example.entity.ProductRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRecipeRepository extends JpaRepository<ProductRecipe, Long> {

    List<ProductRecipe> findByFactoryCode(String factoryCode);

    @Query("select r from ProductRecipe r where r.factoryCode = :fc and r.output.code = :oc")
    Optional<ProductRecipe> findByFactoryCodeAndOutputCode(@Param("fc") String factoryCode,
                                                           @Param("oc") String outputCode);

    List<ProductRecipe> findAllByFactoryCode(String factoryCode);
    List<ProductRecipe> findAllByFactoryCodeAndOutput_Code(String factoryCode, String outputCode);
}