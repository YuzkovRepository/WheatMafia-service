package com.example.example.controller;

import com.example.example.service.VisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    @GetMapping("/by-session/{sessionCode}/user/{userId}")
    public VisitService.VisitState state(@PathVariable String sessionCode, @PathVariable Long userId) {
        return visitService.state(sessionCode, userId);
    }

    /** Отправиться на ферму выбранного игрока. */
    @PostMapping("/by-session/{sessionCode}/user/{userId}/start")
    public VisitService.VisitState start(@PathVariable String sessionCode, @PathVariable Long userId,
                                         @RequestBody StartRequest req) {
        return visitService.start(sessionCode, userId, req.targetUserId());
    }

    public record StartRequest(Long targetUserId) {}

    /** Чужая ферма и скупщик на ней (пока идёт визит). */
    @GetMapping("/by-session/{sessionCode}/user/{userId}/view")
    public VisitService.VisitView view(@PathVariable String sessionCode, @PathVariable Long userId) {
        return visitService.view(sessionCode, userId);
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/{visitId}/report")
    public VisitService.ReportResult report(@PathVariable String sessionCode, @PathVariable Long userId,
                                            @PathVariable Long visitId) {
        return visitService.report(sessionCode, userId, visitId);
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/{visitId}/leave")
    public VisitService.VisitState leave(@PathVariable String sessionCode, @PathVariable Long userId,
                                         @PathVariable Long visitId) {
        return visitService.leave(sessionCode, userId, visitId);
    }
}