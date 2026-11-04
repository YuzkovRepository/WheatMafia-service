package com.example.example.dto;

public record SellRequest(String productCode, int quantity, int price, String description) {}