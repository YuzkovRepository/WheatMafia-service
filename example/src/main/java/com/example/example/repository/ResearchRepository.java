package com.example.example.repository;

import com.example.example.entity.Research;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResearchRepository extends JpaRepository<Research, Long> {

    Optional<Research> findByRegionAndCode(String region, String code);

    List<Research> findByRegionAndStage(String region, int stage);

    List<Research> findByRegionOrderByStageAscTitleAsc(String region);

    List<Research> findAll();

    List<Research> findAllByRegion(String region);
}
