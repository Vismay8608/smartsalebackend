package com.eauction.admin.repository;

import com.eauction.admin.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Integer> {

    List<UserRole> findByUserUserIdAndStatus(Integer userId, String status);

    @Query("SELECT ur FROM UserRole ur WHERE ur.user.userId = :userId AND ur.status = 'ACTIVE' AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)")
    List<UserRole> findActiveRoles(Integer userId);

    @Query("SELECT ur.role.roleId FROM UserRole ur WHERE ur.user.userId = :userId AND ur.status = 'ACTIVE'")
    List<Integer> findActiveRoleIdsByUserId(Integer userId);

    boolean existsByUserUserIdAndRoleRoleIdAndStatus(Integer userId, Integer roleId, String status);
}
