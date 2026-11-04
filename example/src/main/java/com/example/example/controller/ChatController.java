package com.example.example.controller;

import com.example.example.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** Список собеседников с непрочитанными. */
    @GetMapping("/by-session/{sessionCode}/user/{userId}")
    public ChatService.Overview overview(@PathVariable String sessionCode, @PathVariable Long userId) {
        return chatService.overview(sessionCode, userId);
    }

    /** Переписка с игроком (отмечает входящие прочитанными). */
    @GetMapping("/by-session/{sessionCode}/user/{userId}/with/{partnerId}")
    public ChatService.Conversation conversation(@PathVariable String sessionCode, @PathVariable Long userId,
                                                 @PathVariable Long partnerId) {
        return chatService.conversation(sessionCode, userId, partnerId);
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/with/{partnerId}")
    public ChatService.MessageDto send(@PathVariable String sessionCode, @PathVariable Long userId,
                                       @PathVariable Long partnerId, @RequestBody SendRequest req) {
        return chatService.send(sessionCode, userId, partnerId, req.text());
    }

    public record SendRequest(String text) {}
}