package com.example.example.service.impl;

import com.example.example.dto.security.*;
import com.example.example.entity.*;
import com.example.example.exception.CommonException;
import com.example.example.repository.UserRepository;
import com.example.example.security.JwtUtil;
import com.example.example.security.Validators.ValidatorUser;
import com.example.example.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ValidatorUser validatorUser;

    @Override
    public RegisterResponseDTO registerUser(RegisterRequestDTO request) {
        validatorUser.validateRegisterRequest(request);

        if (userRepository.existsByEmail(request.email())) {
            throw new CommonException("Логин уже существует");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword_hash(passwordEncoder.encode(request.password()));
        user.setNickName(request.nickName());

        User savedUser = userRepository.save(user);

        logger.info("Пользователь успешно зарегистрировался: {}", savedUser.getEmail());
        return mapToRegisterResponse(savedUser);
    }

    private RegisterResponseDTO mapToRegisterResponse(User user) {
        return new RegisterResponseDTO(
                user.getEmail()
        );
    }

    @Transactional
    @Override
    public LoginResponseDTO authenticate(LoginRequestDTO loginRequestDTO) {
        logger.info("An authentication request was received for: {}", loginRequestDTO.email());
        validatorUser.validateLoginRequest(loginRequestDTO);

        String login = loginRequestDTO.email();
        String password = loginRequestDTO.password();

        User user = userRepository.findByEmail(login)
                .orElseThrow(() -> new CommonException("The user was not found"));

        if (!passwordEncoder.matches(password, user.getPassword_hash())) {
            logger.error("Invalid password for the user: {}", login);
            throw new CommonException("Invalid password");
        }

        String token = jwtUtil.generateToken(user.getEmail());
        logger.info("Сгенерирован JWT-токен для пользователя: {}", user.getEmail());

        return new LoginResponseDTO(
                token,
                user.getUserId(),
                user.getEmail(),
                user.getNickName()
        );
    }
}
