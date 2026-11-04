package com.example.example.dto.security;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "Емаил не может быть пустым") String email,
        String password
) {}