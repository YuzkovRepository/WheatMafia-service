package com.example.example.dto;

public record LobbyUpdatedEvent(String sessionCode, SessionConnectResponseDTO state) {}