package com.example.example.repository;

import com.example.example.entity.BuyOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BuyOrderRepository extends JpaRepository<BuyOrder, Long> {

    List<BuyOrder> findBySessionCodeAndStatusOrderByCreatedAtDesc(String sessionCode, BuyOrder.Status status);

    long countBySessionCodeAndBuyerIdAndStatus(String sessionCode, Long buyerId, BuyOrder.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from BuyOrder o where o.id = :id")
    Optional<BuyOrder> findByIdForUpdate(@Param("id") Long id);
}
