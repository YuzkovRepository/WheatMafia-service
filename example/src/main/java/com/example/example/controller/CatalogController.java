package com.example.example.controller;

import com.example.example.dto.ProductDto;
import com.example.example.dto.RecipeDto;
import com.example.example.entity.Factory;
import com.example.example.entity.Research;
import com.example.example.repository.FactoryRepository;
import com.example.example.repository.ProductRecipeRepository;
import com.example.example.repository.ProductRepository;
import com.example.example.repository.ResearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final FactoryRepository factoryRepository;
    private final ProductRepository productRepository;
    private final ProductRecipeRepository recipeRepository;
    private final ResearchRepository researchRepository;

    @GetMapping("/factories")
    public List<FactoryDto> factories() {
        return factoryRepository.findAllByOrderByKindAscTitleAsc()
                .stream().map(FactoryDto::from).toList();
    }

    @GetMapping("/products")
    public List<ProductDto> products() {
        return productRepository.findAllByOrderByKindAscTitleAsc()
                .stream().map(ProductDto::from).toList();
    }

    @GetMapping("/recipes")
    public List<RecipeDto> recipes() {
        return recipeRepository.findAll()
                .stream().map(RecipeDto::from).toList();
    }

    /**
     * Откуда продукт и на каком этапе он исследуется — по ВСЕМ странам (дерево исследований
     * игрока показывает только его страну). Нужен справочнику и «цепочкам» в исследованиях.
     */
    @GetMapping("/research-index")
    public List<ResearchIndexDto> researchIndex() {
        Map<String, ResearchIndexDto> byOutput = new LinkedHashMap<>();
        for (Research r : researchRepository.findAll()) {
            if (r.getOutputCode() == null) continue;
            ResearchIndexDto cur = byOutput.get(r.getOutputCode());
            if (cur == null || r.getStage() < cur.stage()) {
                byOutput.put(r.getOutputCode(), new ResearchIndexDto(
                        r.getOutputCode(), r.getRegion(), r.getStage(), r.getCode(), r.getFactoryCode()));
            }
        }
        return List.copyOf(byOutput.values());
    }

    /** Весь каталог одним запросом — именно его ждёт Pinia-стор catalog. */
    @GetMapping
    public CatalogDto catalog() {
        return new CatalogDto(factories(), products(), recipes());
    }

    public record CatalogDto(
            List<FactoryDto> factories,
            List<ProductDto> products,
            List<RecipeDto> recipes
    ) {}

    public record ResearchIndexDto(
            String outputCode, String region, int stage, String researchCode, String factoryCode
    ) {}

    public record FactoryDto(
            Long id, String code, String title, String kind, String icon,
            int width, int height, String region, String requiredResearch
    ) {
        static FactoryDto from(Factory f) {
            return new FactoryDto(f.getId(), f.getCode(), f.getTitle(),
                    f.getKind().name(), f.getIcon(), f.getWidth(), f.getHeight(),
                    f.getRegion(), f.getRequiredResearch());
        }
    }
}