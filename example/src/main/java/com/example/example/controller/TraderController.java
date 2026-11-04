package com.example.example.controller;

import com.example.example.dto.TraderDto;
import com.example.example.service.impl.TraderServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trader")
@RequiredArgsConstructor
public class TraderController {

    private final TraderServiceImpl traderService;

    /** Цены скупки по всем продуктам, которые скупщик берёт. */
    @GetMapping("/prices")
    public ResponseEntity<List<TraderDto.Price>> prices() {
        return ResponseEntity.ok(traderService.prices());
    }

    /** Продать скупщику quantity штук продукта со склада. */
    @PostMapping("/by-session/{sessionCode}/user/{userId}/sell")
    public ResponseEntity<TraderDto.SellResult> sell(@PathVariable String sessionCode,
                                                     @PathVariable Long userId,
                                                     @RequestBody TraderDto.SellRequest req) {
        return ResponseEntity.ok(traderService.sell(sessionCode, userId, req));
    }
}