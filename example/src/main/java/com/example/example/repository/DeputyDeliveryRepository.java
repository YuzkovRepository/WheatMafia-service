package com.example.example.repository;

import com.example.example.entity.DeputyDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeputyDeliveryRepository extends JpaRepository<DeputyDelivery, Long> {

    List<DeputyDelivery> findBySessionCode(String sessionCode);

    boolean existsBySessionCodeAndUserIdAndSlot(String sessionCode, Long userId, String slot);

    boolean existsBySessionCodeAndSlot(String sessionCode, String slot);
}