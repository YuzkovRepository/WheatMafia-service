package com.example.example.dto.security;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SuccessResponseDTO {
    private int status;
    private String message;
}
