package com.example.example.dto;

import com.example.example.entity.Session;
import com.example.example.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record SessionConnectResponseDTO(
        int quantity_players,
        String sessionCode,
        String status,
        Long user,
        Integer score_max,
        Integer roundDurationSeconds,
        List<PlayerDTO> players,
        String region,
        Set<String> unlockedResearch,
        Set<String> unlockedRecipes
) {}