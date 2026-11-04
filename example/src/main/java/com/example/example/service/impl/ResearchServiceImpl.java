package com.example.example.service.impl;

import com.example.example.dto.ResearchDto;
import com.example.example.entity.Factory;
import com.example.example.entity.Product;
import com.example.example.entity.ProductRecipe;
import com.example.example.entity.Research;
import com.example.example.entity.UserSession;
import com.example.example.entity.board.Storage;
import com.example.example.exception.CommonException;
import com.example.example.repository.FactoryRepository;
import com.example.example.repository.ProductRecipeRepository;
import com.example.example.repository.ProductRepository;
import com.example.example.repository.ResearchRepository;
import com.example.example.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResearchServiceImpl {

    private final ResearchRepository researchRepository;
    private final UserSessionRepository userSessionRepository;
    private final FactoryRepository factoryRepository;
    private final ProductRepository productRepository;
    private final ProductRecipeRepository recipeRepository;
    private final JsonMapper jsonMapper;

    @Transactional(readOnly = true)
    public ResearchDto.Tree tree(String sessionCode, Long userId) {
        String code = sessionCode.toUpperCase();
        UserSession us = userSessionRepository
                .findBySessionCodeAndUserId(code, userId)
                .orElseThrow(() -> new CommonException("UserSession not found"));
        return buildTree(us);
    }

    @Transactional
    public ResearchDto.Tree research(String sessionCode, Long userId, String researchCode) {
        String code = sessionCode.toUpperCase();
        UserSession us = userSessionRepository
                .findBySessionCodeAndUserId(code, userId)
                .orElseThrow(() -> new CommonException("UserSession not found"));

        if (us.getRegion() == null) {
            throw new CommonException("Region not assigned");
        }

        Research r = researchRepository.findByRegionAndCode(us.getRegion(), researchCode)
                .orElseThrow(() -> new CommonException("Research not found"));

        if (us.getUnlockedResearch().contains(r.getCode())) {
            throw new CommonException("Already researched");
        }
        if (!us.getUnlockedResearch().containsAll(r.getRequires())) {
            throw new CommonException("Requires previous research");
        }

        Storage storage = us.getStorage() == null
                ? new Storage()
                : jsonMapper.convertValue(us.getStorage(), Storage.class);

        for (Research.Cost c : r.getCost()) {
            if (!storage.has(c.getCode(), c.getQty())) {
                throw new CommonException("Not enough " + c.getCode());
            }
        }
        for (Research.Cost c : r.getCost()) {
            storage.remove(c.getCode(), c.getQty());
        }

        us.setStorage(storage);
        us.getUnlockedResearch().add(r.getCode());
        if (r.getFactoryCode() != null && r.getOutputCode() != null) {
            us.getUnlockedRecipes().add(r.getFactoryCode() + ":" + r.getOutputCode());
        }
        userSessionRepository.save(us);

        return buildTree(us);
    }

    private ResearchDto.Tree buildTree(UserSession us) {
        List<Research> all = researchRepository
                .findByRegionOrderByStageAscTitleAsc(us.getRegion());

        List<ResearchDto.Node> nodes = all.stream()
                .map(r -> toNode(r, us))
                .toList();

        Map<String, Integer> storage = us.getStorage() == null
                ? Map.of()
                : us.getStorage().snapshot();

        return new ResearchDto.Tree(us.getRegion(), nodes, storage);
    }

    private ResearchDto.Node toNode(Research r, UserSession us) {
        boolean unlocked = us.getUnlockedResearch().contains(r.getCode());
        boolean available = !unlocked && us.getUnlockedResearch().containsAll(r.getRequires());

        Factory factory = r.getFactoryCode() == null
                ? null
                : factoryRepository.findByCode(r.getFactoryCode()).orElse(null);
        Product output = r.getOutputCode() == null
                ? null
                : productRepository.findByCode(r.getOutputCode()).orElse(null);

        List<ResearchDto.Cost> cost = r.getCost().stream()
                .map(c -> new ResearchDto.Cost(
                        c.getCode(),
                        productRepository.findByCode(c.getCode())
                                .map(Product::getTitle).orElse(c.getCode()),
                        c.getQty()))
                .toList();

        List<ResearchDto.RecipePreview> recipes = new ArrayList<>();

        // 1) research открывает здание → показать все рецепты здания
        if (factory != null
                && r.getCode().startsWith(r.getRegion().toLowerCase() + "_building_")) {
            recipes = recipeRepository.findAllByFactoryCode(factory.getCode())
                    .stream().map(this::toPreview).toList();
        }
        // 2) research открывает конкретный продукт → показать его рецепт(ы)
        else if (factory != null && output != null) {
            recipes = recipeRepository
                    .findAllByFactoryCodeAndOutput_Code(factory.getCode(), output.getCode())
                    .stream().map(this::toPreview).toList();
        }

        return new ResearchDto.Node(
                r.getCode(),
                r.getTitle(),
                r.getStage(),
                factory != null ? factory.getCode() : null,
                factory != null ? factory.getTitle() : null,
                output != null ? output.getCode() : null,
                output != null ? output.getTitle() : null,
                cost,
                r.getRequires(),
                unlocked,
                available,
                recipes);
    }

    private ResearchDto.RecipePreview toPreview(ProductRecipe recipe) {
        Factory f = factoryRepository.findByCode(recipe.getFactoryCode()).orElse(null);
        return new ResearchDto.RecipePreview(
                recipe.getFactoryCode(),
                f != null ? f.getTitle() : recipe.getFactoryCode(),
                recipe.getOutput().getCode(),
                recipe.getOutput().getTitle(),
                recipe.getOutputQty(),
                recipe.getProductionTime(),
                recipe.getInputs().stream()
                        .map(i -> new ResearchDto.Ingredient(
                                i.getCode(),
                                productRepository.findByCode(i.getCode())
                                        .map(Product::getTitle).orElse(i.getCode()),
                                i.getQty()))
                        .toList());
    }
}