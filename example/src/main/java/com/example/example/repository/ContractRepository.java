package com.example.example.repository;

import com.example.example.entity.Contract;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findBySessionCodeAndStatusOrderByCreatedAtDesc(String sessionCode, Contract.Status status);

    long countBySessionCodeAndCreatorIdAndStatus(String sessionCode, Long creatorId, Contract.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.id = :id")
    Optional<Contract> findByIdForUpdate(@Param("id") Long id);
}
