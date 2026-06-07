package com.eauction.admin.repository;

import com.eauction.admin.entity.UserRegister;
import com.eauction.common.enums.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRegisterRepository extends JpaRepository<UserRegister, Integer> {

    Optional<UserRegister> findByUsername(String username);
    Optional<UserRegister> findByEmail(String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM UserRegister u WHERE (u.username = :identifier OR u.email = :identifier) AND u.deletedAt IS NULL")
    Optional<UserRegister> findByUsernameOrEmail(String identifier);

    Page<UserRegister> findByClientClientIdAndUserType(Integer clientId, UserType userType, Pageable pageable);

    List<UserRegister> findByClientClientIdAndUserType(Integer clientId, UserType userType);

    @Query("SELECT u FROM UserRegister u WHERE u.client.clientId = :clientId AND u.deletedAt IS NULL")
    List<UserRegister> findActiveByClientId(Integer clientId);

    @Modifying
    @Query("UPDATE UserRegister u SET u.lastLoginAt = :now, u.previousLoginAt = u.lastLoginAt WHERE u.userId = :userId")
    void updateLoginTimestamp(Integer userId, OffsetDateTime now);
}
