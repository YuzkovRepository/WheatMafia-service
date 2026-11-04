package com.example.example.controller;

import com.example.example.dto.SessionConnectRequestDTO;
import com.example.example.dto.SessionConnectResponseDTO;
import com.example.example.service.SessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/sessions")
@Tag(name = "Сессии", description = "Методы управления сессиями")
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/connect/{userId}")
    public ResponseEntity<SessionConnectResponseDTO> connectToSession(
            @RequestBody SessionConnectRequestDTO dto,
            @PathVariable Long userId) {
        return ResponseEntity.ok(sessionService.connectToSession(dto, userId));
    }

    @PostMapping("/{code}/ready/{userId}")
    public ResponseEntity<SessionConnectResponseDTO> setReady(
            @PathVariable String code,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "true") boolean ready) {
        return ResponseEntity.ok(sessionService.setReady(code, userId, ready));
    }

    @PostMapping("/{code}/start/{userId}")
    public ResponseEntity<SessionConnectResponseDTO> start(
            @PathVariable String code,
            @PathVariable Long userId) {
        return ResponseEntity.ok(sessionService.startSession(code, userId));
    }
}