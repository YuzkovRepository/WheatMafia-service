package com.example.example.service;

import com.example.example.entity.ChatMessage;
import com.example.example.entity.UserSession;
import com.example.example.exception.CommonException;
import com.example.example.repository.ChatMessageRepository;
import com.example.example.repository.CouncilPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;

/**
 * Личные сообщения между игроками партии. Сообщения читают только двое участников переписки.
 * По STOMP уходит лишь сигнал «пришло сообщение» (без текста) на личный топик получателя —
 * текст загружается обычным запросом, поэтому подписка на чужой топик ничего не раскрывает.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_LEN = 500;
    private static final int HISTORY = 200;
    /** Не больше столько сообщений за окно — защита от спама. */
    private static final int RATE_LIMIT = 15;
    private static final int RATE_WINDOW_SECONDS = 30;

    private final ChatMessageRepository messageRepository;
    private final CouncilPlayerRepository playerRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ---------- DTO (время — миллисекунды Unix) ----------

    public record MessageDto(Long id, Long fromId, Long toId, String text, long createdAtMs, boolean mine) {}

    /** Строка списка собеседников. */
    public record PartnerRow(Long userId, String nickName, String region,
                             String lastText, Long lastAtMs, boolean lastMine, long unread) {}

    public record Overview(List<PartnerRow> partners, long totalUnread) {}

    public record Conversation(Long partnerId, String partnerNick, List<MessageDto> messages) {}

    // ---------- API ----------

    @Transactional(readOnly = true)
    public Overview overview(String sessionCode, Long userId) {
        String code = sessionCode.toUpperCase();
        Map<Long, UserSession> players = playersOf(code);
        if (!players.containsKey(userId)) throw new CommonException("UserSession not found");

        List<PartnerRow> rows = new ArrayList<>();
        long total = 0;
        for (Map.Entry<Long, UserSession> e : players.entrySet()) {
            Long pid = e.getKey();
            if (pid.equals(userId)) continue;
            List<ChatMessage> last = messageRepository.conversation(code, userId, pid, PageRequest.of(0, 1));
            long unread = messageRepository
                    .countBySessionCodeAndRecipientIdAndSenderIdAndReadAtIsNull(code, userId, pid);
            total += unread;
            ChatMessage m = last.isEmpty() ? null : last.get(0);
            rows.add(new PartnerRow(pid, nickOf(e.getValue()), e.getValue().getRegion(),
                    m == null ? null : preview(m.getText()),
                    m == null ? null : m.getCreatedAt().toEpochMilli(),
                    m != null && userId.equals(m.getSenderId()),
                    unread));
        }
        // сначала с новыми сообщениями, потом по времени последнего, потом по нику
        rows.sort(Comparator
                .comparingLong(PartnerRow::unread).reversed()
                .thenComparing((PartnerRow r) -> r.lastAtMs() == null ? 0L : r.lastAtMs(), Comparator.reverseOrder())
                .thenComparing(PartnerRow::nickName, String.CASE_INSENSITIVE_ORDER));
        return new Overview(rows, total);
    }

    /** Переписка с игроком; открытие отмечает входящие прочитанными. */
    @Transactional
    public Conversation conversation(String sessionCode, Long userId, Long partnerId) {
        String code = sessionCode.toUpperCase();
        Map<Long, UserSession> players = checkPair(code, userId, partnerId);

        messageRepository.markRead(code, userId, partnerId, Instant.now());

        List<ChatMessage> list = new ArrayList<>(
                messageRepository.conversation(code, userId, partnerId, PageRequest.of(0, HISTORY)));
        Collections.reverse(list); // старые сверху
        return new Conversation(partnerId, nickOf(players.get(partnerId)),
                list.stream().map(m -> toDto(m, userId)).toList());
    }

    @Transactional
    public MessageDto send(String sessionCode, Long userId, Long partnerId, String rawText) {
        String code = sessionCode.toUpperCase();
        checkPair(code, userId, partnerId);

        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty()) throw new CommonException("Message is empty");
        if (text.length() > MAX_LEN) throw new CommonException("Message is too long");

        Instant now = Instant.now();
        long recent = messageRepository.countBySessionCodeAndSenderIdAndCreatedAtAfter(
                code, userId, now.minusSeconds(RATE_WINDOW_SECONDS));
        if (recent >= RATE_LIMIT) throw new CommonException("Too many messages, slow down");

        ChatMessage m = new ChatMessage();
        m.setSessionCode(code);
        m.setSenderId(userId);
        m.setRecipientId(partnerId);
        m.setText(text);
        m.setCreatedAt(now);
        m = messageRepository.save(m);

        // сигнал получателю (без текста)
        Object signal = Map.<String, Object>of("type", "message", "fromId", userId);
        String destination = "/topic/chat." + code + "." + partnerId;
        afterCommit(() -> messagingTemplate.convertAndSend(destination, signal));

        return toDto(m, userId);
    }

    // ---------- внутреннее ----------

    private Map<Long, UserSession> checkPair(String code, Long userId, Long partnerId) {
        if (userId.equals(partnerId)) throw new CommonException("You cannot write to yourself");
        Map<Long, UserSession> players = playersOf(code);
        if (!players.containsKey(userId)) throw new CommonException("UserSession not found");
        if (!players.containsKey(partnerId)) throw new CommonException("Player not found");
        return players;
    }

    private MessageDto toDto(ChatMessage m, Long me) {
        return new MessageDto(m.getId(), m.getSenderId(), m.getRecipientId(), m.getText(),
                m.getCreatedAt().toEpochMilli(), me.equals(m.getSenderId()));
    }

    private String preview(String t) {
        return t.length() <= 60 ? t : t.substring(0, 57) + "…";
    }

    private Map<Long, UserSession> playersOf(String code) {
        Map<Long, UserSession> map = new LinkedHashMap<>();
        for (UserSession us : playerRepository.findAllBySessionCode(code)) {
            map.put(us.getUser().getUserId(), us);
        }
        return map;
    }

    private String nickOf(UserSession us) {
        String nick = us.getUser().getNickName();
        return nick == null || nick.isBlank() ? "Игрок" : nick;
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { r.run(); }
            });
        } else {
            r.run();
        }
    }
}