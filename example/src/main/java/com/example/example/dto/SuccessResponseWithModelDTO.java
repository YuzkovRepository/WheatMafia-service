package com.example.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SuccessResponseWithModelDTO<T> {
    private int status;
    private String message;
    private T data;
}
