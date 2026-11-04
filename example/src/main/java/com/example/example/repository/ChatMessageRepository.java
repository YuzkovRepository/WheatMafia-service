package com.example.example.repository;

import com.example.example.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Переписка двух игроков, новые первыми (размер задаёт Pageable). */
    @Query("select m from ChatMessage m where m.sessionCode = :code and "
            + "((m.senderId = :a and m.recipientId = :b) or (m.senderId = :b and m.recipientId = :a)) "
            + "order by m.id desc")
    List<ChatMessage> conversation(@Param("code") String code, @Param("a") Long a, @Param("b") Long b, Pageable page);

    long countBySessionCodeAndRecipientIdAndSenderIdAndReadAtIsNull(String code, Long recipientId, Long senderId);

    long countBySessionCodeAndSenderIdAndCreatedAtAfter(String code, Long senderId, Instant after);

    @Modifying
    @Query("update ChatMessage m set m.readAt = :now where m.sessionCode = :code and "
            + "m.recipientId = :me and m.senderId = :other and m.readAt is null")
    int markRead(@Param("code") String code, @Param("me") Long me, @Param("other") Long other, @Param("now") Instant now);
}