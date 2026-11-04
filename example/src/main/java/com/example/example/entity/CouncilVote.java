package com.example.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "council_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "voter_id"}))
public class CouncilVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "voter_id", nullable = false)
    private Long voterId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;
}