package com.example.example.dto;

import jakarta.persistence.Column;

public record SessionConnectRequestDTO(
        @Column(length = 4)
        String sessionCode,
        Integer roundDurationSeconds,
        Integer score_max
) {}
