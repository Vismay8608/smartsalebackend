package com.eauction.admin.repository;

import com.eauction.admin.entity.ClientBranchLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientBranchLevelRepository extends JpaRepository<ClientBranchLevel, Integer> {

    List<ClientBranchLevel> findByClientClientIdOrderByLevelOrderAsc(Integer clientId);

    @Query("SELECT l FROM ClientBranchLevel l WHERE l.client.clientId = :clientId AND l.isAdminBranch = true")
    Optional<ClientBranchLevel> findAdminLevel(Integer clientId);

    boolean existsByClientClientIdAndLevelCode(Integer clientId, String levelCode);
}
