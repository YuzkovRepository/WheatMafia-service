package com.example.example.service;


import com.example.example.dto.SessionConnectRequestDTO;
import com.example.example.dto.SessionConnectResponseDTO;

public interface SessionService {
    SessionConnectResponseDTO connectToSession(SessionConnectRequestDTO dto, Long userId);
    SessionConnectResponseDTO setReady(String sessionCode, Long userId, boolean ready);
    SessionConnectResponseDTO startSession(String sessionCode, Long userId);
}