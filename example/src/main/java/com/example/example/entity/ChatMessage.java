package com.example.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Личное сообщение одного игрока другому в рамках партии. Видят только отправитель и получатель. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_conv", columnList = "session_code, sender_id, recipient_id"),
                @Index(name = "idx_chat_unread", columnList = "session_code, recipient_id, read_at")
        })
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", length = 32, nullable = false)
    private String sessionCode;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(length = 500, nullable = false)
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Когда получатель прочитал; null — не прочитано. */
    @Column(name = "read_at")
    private Instant readAt;
}