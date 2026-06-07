package com.eauction.web.repository;

import com.eauction.web.entity.UserFailedLogin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserFailedLoginRepository extends JpaRepository<UserFailedLogin, Integer> {

    Optional<UserFailedLogin> findByUserId(Integer userId);

    @Modifying
    @Query("UPDATE UserFailedLogin f SET f.failedAttemptCount = 0, f.accountLocked = false, f.lockUntil = null WHERE f.userId = :userId")
    void resetFailedAttempts(Integer userId);
}
