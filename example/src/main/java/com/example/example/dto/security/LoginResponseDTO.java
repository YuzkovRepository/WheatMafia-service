package com.example.example.dto.security;

public record LoginResponseDTO(
        String token,
        Long id,
        String email,
        String nickName
) {}