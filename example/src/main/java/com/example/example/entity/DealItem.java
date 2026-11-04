package com.example.example.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Позиция контракта: продукт + количество. Хранится в JSON-колонке контракта. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DealItem {
    private String code;
    private int qty;
}

