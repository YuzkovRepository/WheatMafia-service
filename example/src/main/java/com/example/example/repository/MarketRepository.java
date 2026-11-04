package com.example.example.repository;

import com.example.example.entity.Market;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarketRepository extends JpaRepository<Market, Long> {

    List<Market> findBySessionCodeAndStatusOrderByCreatedAtDesc(String sessionCode, Market.Status status);

    long countBySessionCodeAndSellerIdAndStatus(String sessionCode, Long sellerId, Market.Status status);

    /** Блокировка лота на время покупки/снятия, чтобы два игрока не купили один лот. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Market m where m.slotId = :id")
    Optional<Market> findByIdForUpdate(@Param("id") Long id);
}