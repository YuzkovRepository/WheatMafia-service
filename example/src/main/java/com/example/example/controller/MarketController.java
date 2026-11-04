package com.example.example.controller;

import com.example.example.dto.MarketOverview;
import com.example.example.dto.SellRequest;
import com.example.example.service.impl.MarketServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketServiceImpl marketService;

    /** Баланс игрока + журнал всех активных лотов партии. */
    @GetMapping("/by-session/{sessionCode}/user/{userId}")
    public ResponseEntity<MarketOverview> overview(@PathVariable String sessionCode,
                                                   @PathVariable Long userId) {
        return ResponseEntity.ok(marketService.overview(sessionCode, userId));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/sell")
    public ResponseEntity<MarketOverview> sell(@PathVariable String sessionCode,
                                               @PathVariable Long userId,
                                               @RequestBody SellRequest req) {
        return ResponseEntity.ok(marketService.sell(sessionCode, userId, req));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/buy/{slotId}")
    public ResponseEntity<MarketOverview> buy(@PathVariable String sessionCode,
                                              @PathVariable Long userId,
                                              @PathVariable Long slotId) {
        return ResponseEntity.ok(marketService.buy(sessionCode, userId, slotId));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/cancel/{slotId}")
    public ResponseEntity<MarketOverview> cancel(@PathVariable String sessionCode,
                                                 @PathVariable Long userId,
                                                 @PathVariable Long slotId) {
        return ResponseEntity.ok(marketService.cancel(sessionCode, userId, slotId));
    }
}
