package com.example.example.service.impl;

import com.example.example.entity.BlackMarketVisit;
import com.example.example.repository.BlackMarketVisitRepository;
import com.example.example.service.BuyerPresence;
import com.example.example.service.impl.BlackMarketServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** Связка визитов на чужие фермы с теневым скупщиком (BlackMarketServiceImpl). */
@Component
@RequiredArgsConstructor
public class BlackMarketBuyerPresence implements BuyerPresence {

    /** Те же 6 секунд, что скупщик «уезжает» в конце визита: уезжающего уже не поймать. */
    private static final long DEPART_SECONDS = 6;

    private final BlackMarketVisitRepository visitRepository;
    private final BlackMarketServiceImpl blackMarket;

    @Override
    @Transactional(readOnly = true)
    public Optional<BuyerWindow> activeBuyer(String sessionCode, Long userId, Instant now) {
        return visitRepository.findFirstBySessionCodeAndUserIdOrderByIdDesc(sessionCode, userId)
                .filter(v -> now.isBefore(v.getLeavesAt().minusSeconds(DEPART_SECONDS)))
                .map(this::toWindow);
    }

    /** Как кнопка «отпустить»: скупщик уезжает, не продав товар. */
    @Override
    @Transactional
    public void scare(String sessionCode, Long userId) {
        blackMarket.dismiss(sessionCode, userId);
    }

    private BuyerWindow toWindow(BlackMarketVisit v) {
        return new BuyerWindow(v.getSummonedAt().toEpochMilli(), v.getArrivesAt().toEpochMilli(),
                v.getLeavesAt().toEpochMilli());
    }
}