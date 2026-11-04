package com.example.example.controller;

import com.example.example.service.CouncilService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/council")
@RequiredArgsConstructor
public class CouncilController {

    private final CouncilService councilService;

    @GetMapping("/by-session/{sessionCode}/user/{userId}")
    public CouncilService.CouncilState state(@PathVariable String sessionCode, @PathVariable Long userId) {
        return councilService.state(sessionCode, userId);
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/vote")
    public CouncilService.CouncilState vote(@PathVariable String sessionCode,
                                            @PathVariable Long userId,
                                            @RequestBody VoteRequest req) {
        return councilService.vote(sessionCode, userId, req.targetUserId());
    }

    public record VoteRequest(Long targetUserId) {}
}