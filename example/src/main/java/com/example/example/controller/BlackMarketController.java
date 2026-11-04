package com.example.example.controller;

import com.example.example.dto.BlackMarketDto;
import com.example.example.service.impl.BlackMarketServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/blackmarket")
@RequiredArgsConstructor
public class BlackMarketController {

    private final BlackMarketServiceImpl service;

    @GetMapping("/by-session/{sessionCode}/user/{userId}")
    public BlackMarketDto.State state(@PathVariable String sessionCode, @PathVariable Long userId) {
        return service.state(sessionCode, userId);
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/summon")
    public BlackMarketDto.State summon(@PathVariable String sessionCode, @PathVariable Long userId) {
        return service.summon(sessionCode, userId);
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/dismiss")
    public BlackMarketDto.State dismiss(@PathVariable String sessionCode, @PathVariable Long userId) {
        return service.dismiss(sessionCode, userId);
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/sell")
    public BlackMarketDto.SellResult sell(@PathVariable String sessionCode,
                                          @PathVariable Long userId,
                                          @RequestBody(required = false) BlackMarketDto.SellRequest req) {
        return service.sell(sessionCode, userId, req);
    }
}