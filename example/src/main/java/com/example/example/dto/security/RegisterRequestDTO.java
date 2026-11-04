package com.example.example.dto.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank(message = "Емаил не должен быть пустым.")
        @Email(message = "Емаил не должен быть пустым")
        String email,

        @NotBlank(message = "Пароль не должен быть пустым")
        @Size(min = 8, max = 20, message = "Пароль должен содержать от 8 до 20 символов")
        @Pattern( regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=\\S+$).*$",
                message = "Пароль должен содержать как минимум одну цифру, одну строчную букву, одну заглавную букву и не содержать пробелов")
        String password,

        @Size(min = 8, max = 20, message = "Имя должно содержать от 8 до 20 символов")
        String nickName
) {}