package com.example.example.security.Validators;

import com.example.example.dto.security.LoginRequestDTO;
import com.example.example.dto.security.RegisterRequestDTO;
import org.springframework.stereotype.Component;

@Component
public class ValidatorUser {
    public void validateRegisterRequest(RegisterRequestDTO loginRequestDTO) {
        if (loginRequestDTO.password() == null || loginRequestDTO.password().isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть нулевым или пустым");
        }
        if (loginRequestDTO.email() == null || loginRequestDTO.email().isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть нулевым или пустым");
        }
    }

    public void validateLoginRequest(LoginRequestDTO requestDTO) {
        if (requestDTO.email() == null || requestDTO.email().isEmpty()) {
            throw new IllegalArgumentException("Адрес электронной почты не может быть пустым");
        }
        if (requestDTO.password() == null || requestDTO.password().isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть нулевым или незаполненным");
        }
    }
}
