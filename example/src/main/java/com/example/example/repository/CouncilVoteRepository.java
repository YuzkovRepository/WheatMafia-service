package com.example.example.repository;

import com.example.example.entity.CouncilVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouncilVoteRepository extends JpaRepository<CouncilVote, Long> {

    List<CouncilVote> findByRoundId(Long roundId);

    Optional<CouncilVote> findByRoundIdAndVoterId(Long roundId, Long voterId);
}