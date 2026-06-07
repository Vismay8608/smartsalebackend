package com.eauction.web.repository;

import com.eauction.web.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Integer> {

    Optional<UserSession> findBySessionId(String sessionId);
    Optional<UserSession> findByJti(UUID jti);
    Optional<UserSession> findByRefreshToken(String refreshToken);

    List<UserSession> findByUserIdAndIsActiveTrue(Integer userId);

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.userId = :userId AND s.isActive = true AND s.isRevoked = false")
    long countActiveSessions(Integer userId);

    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false, s.isRevoked = true, s.revokedAt = :now, s.revokeReason = :reason " +
           "WHERE s.userId = :userId AND s.isActive = true")
    void revokeAllForUser(Integer userId, OffsetDateTime now, String reason);

    @Modifying
    @Query("UPDATE UserSession s SET s.lastActivityAt = :now WHERE s.sessionId = :sessionId")
    void updateActivity(String sessionId, OffsetDateTime now);
}
