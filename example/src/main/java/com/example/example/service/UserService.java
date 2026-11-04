package com.example.example.service;

import com.example.example.dto.security.*;

import java.util.List;

public interface UserService {
    RegisterResponseDTO registerUser(RegisterRequestDTO requestDTO);
    LoginResponseDTO authenticate(LoginRequestDTO requestDTO);
}