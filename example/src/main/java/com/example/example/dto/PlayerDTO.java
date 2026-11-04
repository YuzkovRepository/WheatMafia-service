package com.example.example.dto;

public record PlayerDTO(
        Long userId,
        Long userSessionId,
        String nickName,
        boolean isHost,
        boolean isReady,
        String region
) {}