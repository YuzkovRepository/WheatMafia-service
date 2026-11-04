package com.example.example.controller;

import com.example.example.entity.Factory;
import com.example.example.repository.FactoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/factory")
@RequiredArgsConstructor
public class FactoryController {

    private final FactoryRepository productionRepository;

    @GetMapping
    public List<CatalogItemDto> catalog() {
        return productionRepository.findAllByOrderByKindAscTitleAsc()
                .stream()
                .map(CatalogItemDto::from)
                .toList();
    }

    public record CatalogItemDto(
            Long id,
            String code,
            String title,
            String kind,
            String icon,
            int width,
            int height,
            String region
    ) {
        static CatalogItemDto from(Factory p) {
            return new CatalogItemDto(
                    p.getId(),
                    p.getCode(),
                    p.getTitle(),
                    p.getKind().name(),
                    p.getIcon(),
                    p.getWidth(),
                    p.getHeight(),
                    p.getRegion()
            );
        }
    }
}