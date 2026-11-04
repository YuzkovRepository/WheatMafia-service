package com.example.example.repository;

import com.example.example.entity.BlackMarketVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BlackMarketVisitRepository extends JpaRepository<BlackMarketVisit, Long> {

    Optional<BlackMarketVisit> findFirstBySessionCodeAndUserIdOrderByIdDesc(String sessionCode, Long userId);

    /** Визиты, которые ещё не закончились (скупщик в пути или на месте). */
    List<BlackMarketVisit> findBySessionCodeAndLeavesAtAfter(String sessionCode, Instant after);
}