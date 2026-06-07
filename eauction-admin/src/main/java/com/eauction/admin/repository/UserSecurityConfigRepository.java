package com.eauction.admin.repository;

import com.eauction.admin.entity.UserSecurityConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSecurityConfigRepository extends JpaRepository<UserSecurityConfig, Integer> {
    Optional<UserSecurityConfig> findByClientClientId(Integer clientId);
}
