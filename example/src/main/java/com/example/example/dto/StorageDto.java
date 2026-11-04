package com.example.example.dto;

import com.example.example.entity.board.Storage;

import java.util.Map;

public record StorageDto(int capacity, int used, Map<String, Integer> items) {
    public static StorageDto from(Storage s) {
        return new StorageDto(s.getCapacity(), s.used(), s.snapshot());
    }
}