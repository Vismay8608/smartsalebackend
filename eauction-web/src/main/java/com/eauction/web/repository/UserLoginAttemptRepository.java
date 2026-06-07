package com.eauction.web.repository;

import com.eauction.web.entity.UserLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLoginAttemptRepository extends JpaRepository<UserLoginAttempt, Integer> {
}
