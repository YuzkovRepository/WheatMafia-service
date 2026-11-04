package com.example.example.repository;

import com.example.example.entity.Factory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FactoryRepository extends JpaRepository<Factory, Long> {
    List<Factory> findAllByOrderByKindAscTitleAsc();

    Optional<Factory> findByCode(String code);
    List<Factory> findAllByRegion(String region);
}
