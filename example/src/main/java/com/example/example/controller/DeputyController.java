package com.example.example.controller;

import com.example.example.dto.DeputyDto;
import com.example.example.service.impl.DeputyServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/deputy")
@RequiredArgsConstructor
public class DeputyController {

    private final DeputyServiceImpl deputyService;

    @GetMapping("/by-session/{sessionCode}")
    public DeputyDto.State state(@PathVariable String sessionCode) {
        return deputyService.state(sessionCode);
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/feed")
    public DeputyDto.State feed(@PathVariable String sessionCode,
                                @PathVariable Long userId,
                                @RequestBody FeedRequest req) {
        return deputyService.feed(sessionCode, userId, req.productCode());
    }

    public record FeedRequest(String productCode) {}
}