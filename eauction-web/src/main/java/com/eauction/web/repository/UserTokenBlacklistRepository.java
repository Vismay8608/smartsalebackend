package com.eauction.web.repository;

import com.eauction.web.entity.UserTokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface UserTokenBlacklistRepository extends JpaRepository<UserTokenBlacklist, Integer> {

    boolean existsByJti(UUID jti);

    @Modifying
    @Query("UPDATE UserTokenBlacklist b SET b.isCleaned = true WHERE b.originalExpiresAt < :now AND b.isCleaned = false")
    int cleanExpired(OffsetDateTime now);
}
