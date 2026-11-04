package com.example.example.controller;

import com.example.example.dto.ResearchDto;
import com.example.example.service.impl.ResearchServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/research")
@RequiredArgsConstructor
public class ResearchController {

    private final ResearchServiceImpl researchService;

    @GetMapping("/by-session/{sessionCode}/user/{userId}")
    public ResearchDto.Tree tree(@PathVariable String sessionCode, @PathVariable Long userId) {
        return researchService.tree(sessionCode, userId);
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/open/{researchCode}")
    public ResearchDto.Tree open(@PathVariable String sessionCode,
                                 @PathVariable Long userId,
                                 @PathVariable String researchCode) {
        return researchService.research(sessionCode, userId, researchCode);
    }
}