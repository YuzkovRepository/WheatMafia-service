package com.example.example.controller;

import com.example.example.dto.DealDto;
import com.example.example.service.impl.DealServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
public class DealController {

    private final DealServiceImpl dealService;

    /** Баланс игрока + все активные контракты и суперзаказы партии. */
    @GetMapping("/by-session/{sessionCode}/user/{userId}")
    public ResponseEntity<DealDto.Overview> overview(@PathVariable String sessionCode,
                                                     @PathVariable Long userId) {
        return ResponseEntity.ok(dealService.overview(sessionCode, userId));
    }

    // ---------- контракты ----------

    @PostMapping("/by-session/{sessionCode}/user/{userId}/contracts")
    public ResponseEntity<DealDto.Overview> createContract(@PathVariable String sessionCode,
                                                           @PathVariable Long userId,
                                                           @RequestBody DealDto.CreateContract req) {
        return ResponseEntity.ok(dealService.createContract(sessionCode, userId, req));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/contracts/{id}/accept")
    public ResponseEntity<DealDto.Overview> acceptContract(@PathVariable String sessionCode,
                                                           @PathVariable Long userId,
                                                           @PathVariable Long id) {
        return ResponseEntity.ok(dealService.acceptContract(sessionCode, userId, id));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/contracts/{id}/cancel")
    public ResponseEntity<DealDto.Overview> cancelContract(@PathVariable String sessionCode,
                                                           @PathVariable Long userId,
                                                           @PathVariable Long id) {
        return ResponseEntity.ok(dealService.cancelContract(sessionCode, userId, id));
    }

    // ---------- суперзаказы ----------

    @PostMapping("/by-session/{sessionCode}/user/{userId}/orders")
    public ResponseEntity<DealDto.Overview> createOrder(@PathVariable String sessionCode,
                                                        @PathVariable Long userId,
                                                        @RequestBody DealDto.CreateOrder req) {
        return ResponseEntity.ok(dealService.createOrder(sessionCode, userId, req));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/orders/{id}/fulfill")
    public ResponseEntity<DealDto.Overview> fulfillOrder(@PathVariable String sessionCode,
                                                         @PathVariable Long userId,
                                                         @PathVariable Long id,
                                                         @RequestBody DealDto.FulfillOrder req) {
        return ResponseEntity.ok(dealService.fulfillOrder(sessionCode, userId, id, req));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/orders/{id}/cancel")
    public ResponseEntity<DealDto.Overview> cancelOrder(@PathVariable String sessionCode,
                                                        @PathVariable Long userId,
                                                        @PathVariable Long id) {
        return ResponseEntity.ok(dealService.cancelOrder(sessionCode, userId, id));
    }
}
