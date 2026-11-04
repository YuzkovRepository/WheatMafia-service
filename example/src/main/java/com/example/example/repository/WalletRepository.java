package com.example.example.repository;

import com.example.example.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /** Кошелёк с блокировкой на запись — чтобы параллельные сделки не потеряли монеты. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.sessionCode = :code and w.userId = :userId")
    Optional<Wallet> findForUpdate(@Param("code") String code, @Param("userId") Long userId);
}