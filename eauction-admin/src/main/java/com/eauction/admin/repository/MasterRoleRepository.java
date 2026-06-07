package com.eauction.admin.repository;

import com.eauction.admin.entity.MasterRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasterRoleRepository extends JpaRepository<MasterRole, Integer> {

    boolean existsByClientClientIdAndRoleCode(Integer clientId, String roleCode);

    List<MasterRole> findByClientClientIdAndActorTypeAndIsActiveTrue(Integer clientId, String actorType);

    Optional<MasterRole> findByClientClientIdAndRoleCode(Integer clientId, String roleCode);

    @Query("SELECT r FROM MasterRole r WHERE r.client.clientId = :clientId AND r.isActive = true ORDER BY r.displayOrder ASC")
    List<MasterRole> findActiveRolesForClient(Integer clientId);
}
