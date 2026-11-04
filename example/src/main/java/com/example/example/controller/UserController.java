package com.example.example.controller;

import com.example.example.dto.SuccessResponseDTO;
import com.example.example.dto.SuccessResponseWithModelDTO;
import com.example.example.dto.security.*;
import com.example.example.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "Пользователи", description = "Методы регистрации, аутентификации и управления ролями/правами пользователей")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    @Operation(summary = "Регистрация пользователя", description = "Создаёт нового пользователя и возвращает данные зарегистрированного пользователя")
    @ApiResponse(responseCode = "201", description = "Пользователь успешно зарегистрирован")
    @PostMapping("/register")
    public ResponseEntity<SuccessResponseWithModelDTO> registerUser(@RequestBody RegisterRequestDTO requestDTO) {
        RegisterResponseDTO registeredUser = userService.registerUser(requestDTO);
        SuccessResponseWithModelDTO response = new SuccessResponseWithModelDTO(
                HttpStatus.CREATED.value(),
                "The user has successfully registered",
                registeredUser
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Аутентификация пользователя", description = "Возвращает JWT-токен при успешной аутентификации")
    @ApiResponse(responseCode = "202", description = "Пользователь успешно аутентифицирован")
    @PostMapping("/login")
    public ResponseEntity<SuccessResponseWithModelDTO> authenticate(@Validated @RequestBody LoginRequestDTO requestDTO) {
        LoginResponseDTO authResponse = userService.authenticate(requestDTO);
        SuccessResponseWithModelDTO response = new SuccessResponseWithModelDTO(
                HttpStatus.ACCEPTED.value(),
                "The user has successfully logged in",
                authResponse
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
