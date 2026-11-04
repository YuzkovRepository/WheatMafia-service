package com.example.example.repository;

import com.example.example.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CouncilPlayerRepository extends JpaRepository<UserSession, Long> {

    @Query("select us from UserSession us where us.session.sessionCode = :code")
    List<UserSession> findAllBySessionCode(@Param("code") String code);
}