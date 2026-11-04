package com.example.example.config;

import com.example.example.dto.LobbyUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LobbyEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLobbyUpdated(LobbyUpdatedEvent e) {
        messagingTemplate.convertAndSend("/topic/lobby." + e.sessionCode(), e.state());
    }
}
