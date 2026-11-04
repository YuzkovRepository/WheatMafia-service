package com.example.example.repository;

import com.example.example.entity.CouncilRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouncilRoundRepository extends JpaRepository<CouncilRound, Long> {

    Optional<CouncilRound> findTopBySessionCodeOrderByRoundNoDesc(String sessionCode);

    Optional<CouncilRound> findTopBySessionCodeAndResolvedTrueOrderByRoundNoDesc(String sessionCode);
}