package com.example.example.repository;

import com.example.example.entity.Session;
import com.example.example.entity.User;
import com.example.example.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    boolean existsByUserAndSession(User user, Session session);

    Optional<UserSession> findByUserAndSession(User user, Session session);

    @Query("""
        SELECT us FROM UserSession us
        JOIN us.session s
        JOIN us.user u
        WHERE s.sessionCode = :code AND u.userId = :userId
    """)
    Optional<UserSession> findBySessionCodeAndUserId(@Param("code") String code,
                                                     @Param("userId") Long userId);

    long countBySessionAndRegion(Session session, String region);
}