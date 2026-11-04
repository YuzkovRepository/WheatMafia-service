package com.example.example.repository;

import com.example.example.entity.FarmVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FarmVisitRepository extends JpaRepository<FarmVisit, Long> {

    Optional<FarmVisit> findFirstBySessionCodeAndVisitorIdAndStatus(String sessionCode, Long visitorId, String status);

    /** Все визиты игрока в партии, новые первыми. */
    List<FarmVisit> findBySessionCodeAndVisitorIdOrderByStartedAtDesc(String sessionCode, Long visitorId);

    long countBySessionCodeAndVisitorIdAndStatusNot(String sessionCode, Long visitorId, String status);
}