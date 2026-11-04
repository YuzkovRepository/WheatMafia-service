package com.example.example.dto.security;

public record ErrorResponseDTO(
        int status,
        String message
) {}