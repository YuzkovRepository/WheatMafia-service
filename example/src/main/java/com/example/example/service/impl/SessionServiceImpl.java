package com.example.example.service.impl;

import com.example.example.config.SessionCodeGenerator;
import com.example.example.dto.*;
import com.example.example.entity.Research;
import com.example.example.entity.Session;
import com.example.example.entity.User;
import com.example.example.entity.UserSession;
import com.example.example.exception.CommonException;
import com.example.example.repository.ResearchRepository;
import com.example.example.repository.SessionRepository;
import com.example.example.repository.UserRepository;
import com.example.example.repository.UserSessionRepository;
import com.example.example.service.SessionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private static final List<String> ALL_REGIONS = List.of("ITALY", "JAPAN", "EGYPT", "BRAZIL");

    private final SessionRepository sessionRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final ResearchRepository researchRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public SessionConnectResponseDTO connectToSession(SessionConnectRequestDTO dto, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException("User not found"));

        Session session;

        if (dto.sessionCode() == null || dto.sessionCode().isBlank()) {
            session = new Session();
            session.setSessionCode(generateUniqueSessionCode());
            if (dto.roundDurationSeconds() != null) session.setRoundDurationSeconds(dto.roundDurationSeconds());
            if (dto.score_max() != null) session.setScore_max(dto.score_max());
            session = sessionRepository.save(session);
        } else {
            session = sessionRepository.findBySessionCode(dto.sessionCode().toUpperCase())
                    .orElseThrow(() -> new CommonException("Комната не найдена"));

            if (session.getStatus() == Session.Status.COMPLETED) {
                throw new CommonException("Комната уже завершена");
            }
            if (session.getQuantity_players() >= 6) {
                throw new CommonException("Комната заполнена");
            }
            if (userSessionRepository.existsByUserAndSession(user, session)) {
                session = sessionRepository.findById(session.getSessionId())
                        .orElseThrow(() -> new CommonException("Session not found"));
                return mapSessionToDTO(session, user.getUserId());
            }

            session.setQuantity_players(session.getQuantity_players() + 1);
            sessionRepository.save(session);
        }

        // === Новое: назначение роли-региона и стартовых исследований ===
        String region = pickRegion(session, userId);

        UserSession userSession = new UserSession();
        userSession.setUser(user);
        userSession.setSession(session);
        userSession.setBalance(0);
        userSession.setReputation(5);
        userSession.setReady(false);
        userSession.setRegion(region);

        List<Research> starters = researchRepository.findByRegionAndStage(region, 1);
        Set<String> unlockedResearch = starters.stream()
                .map(Research::getCode).collect(Collectors.toCollection(HashSet::new));
        Set<String> unlockedRecipes = starters.stream()
                .map(r -> r.getFactoryCode() + ":" + r.getOutputCode())
                .collect(Collectors.toCollection(HashSet::new));
        userSession.setUnlockedResearch(unlockedResearch);
        userSession.setUnlockedRecipes(unlockedRecipes);

        userSessionRepository.save(userSession);

        session = sessionRepository.findById(session.getSessionId())
                .orElseThrow(() -> new CommonException("Session not found"));

        return mapAndPublish(session, user.getUserId());
    }

    @Override
    @Transactional
    public SessionConnectResponseDTO setReady(String sessionCode, Long userId, boolean ready) {
        Session session = sessionRepository.findBySessionCode(sessionCode.toUpperCase())
                .orElseThrow(() -> new CommonException("Комната не найдена"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException("User not found"));

        UserSession us = userSessionRepository.findByUserAndSession(user, session)
                .orElseThrow(() -> new CommonException("Вы не в этой комнате"));

        us.setReady(ready);
        userSessionRepository.save(us);

        session = sessionRepository.findById(session.getSessionId()).orElseThrow();
        return mapAndPublish(session, userId);
    }

    @Override
    @Transactional
    public SessionConnectResponseDTO startSession(String sessionCode, Long userId) {
        Session session = sessionRepository.findBySessionCode(sessionCode.toUpperCase())
                .orElseThrow(() -> new CommonException("Комната не найдена"));

        List<UserSession> all = session.getUserSessions().stream()
                .sorted(Comparator.comparing(UserSession::getUserSessionId))
                .toList();
        if (all.isEmpty()) throw new CommonException("В комнате нет игроков");
        Long hostId = all.get(0).getUser().getUserId();
        if (!hostId.equals(userId)) {
            throw new CommonException("Только хост может начать игру");
        }
        if (all.size() < 2) {
            throw new CommonException("Нужно минимум 2 игрока");
        }
        boolean allReady = all.stream().allMatch(UserSession::isReady);
        if (!allReady) {
            throw new CommonException("Не все игроки готовы");
        }

        session.setStatus(Session.Status.IN_PROCESS);
        session.setRoundStartedAt(java.time.LocalDateTime.now());
        sessionRepository.save(session);

        return mapAndPublish(session, userId);
    }

    /** Роль: берём регион с наименьшим числом игроков и рандомим из этой группы. */
    private String pickRegion(Session session, Long userId) {
        Map<String, Long> counts = ALL_REGIONS.stream()
                .collect(Collectors.toMap(r -> r,
                        r -> userSessionRepository.countBySessionAndRegion(session, r)));
        long min = counts.values().stream().mapToLong(Long::longValue).min().orElse(0);
        List<String> candidates = counts.entrySet().stream()
                .filter(e -> e.getValue() == min)
                .map(Map.Entry::getKey)
                .toList();
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private String generateUniqueSessionCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = SessionCodeGenerator.generate();
            if (!sessionRepository.existsBySessionCode(code)) return code;
        }
        throw new CommonException("Не удалось сгенерировать уникальный код");
    }

    private SessionConnectResponseDTO mapAndPublish(Session session, Long userId) {
        eventPublisher.publishEvent(
                new LobbyUpdatedEvent(session.getSessionCode(), mapSessionToDTO(session, null)));
        return mapSessionToDTO(session, userId);
    }

    private SessionConnectResponseDTO mapSessionToDTO(Session session, Long userId) {
        List<UserSession> all = session.getUserSessions().stream()
                .sorted(Comparator.comparing(UserSession::getUserSessionId))
                .toList();

        Long hostId = all.isEmpty() ? null : all.get(0).getUser().getUserId();

        List<PlayerDTO> players = all.stream()
                .map(us -> new PlayerDTO(
                        us.getUser().getUserId(),
                        us.getUserSessionId(),
                        us.getUser().getNickName(),
                        us.getUser().getUserId().equals(hostId),
                        us.isReady(),
                        us.getRegion()
                ))
                .toList();

        // Для «себя» отдаём открытия; для чужих они скрыты
        UserSession self = userId == null ? null : all.stream()
                .filter(us -> us.getUser().getUserId().equals(userId))
                .findFirst().orElse(null);

        return new SessionConnectResponseDTO(
                session.getQuantity_players(),
                session.getSessionCode(),
                session.getStatus() != null ? session.getStatus().name() : "PENDING",
                userId,
                session.getScore_max(),
                session.getRoundDurationSeconds(),
                players,
                self != null ? self.getRegion() : null,
                self != null ? self.getUnlockedResearch() : null,
                self != null ? self.getUnlockedRecipes() : null
        );
    }
}