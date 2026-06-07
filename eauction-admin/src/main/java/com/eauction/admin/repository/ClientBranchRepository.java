package com.eauction.admin.repository;

import com.eauction.admin.entity.ClientBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientBranchRepository extends JpaRepository<ClientBranch, Integer> {

    boolean existsByBranchCode(String branchCode);

    List<ClientBranch> findByClientClientIdOrderByCreatedAtAsc(Integer clientId);

    @Query("SELECT b FROM ClientBranch b WHERE b.client.clientId = :clientId AND b.parent IS NULL")
    Optional<ClientBranch> findRootBranch(Integer clientId);

    @Query("SELECT b FROM ClientBranch b WHERE b.client.clientId = :clientId AND b.level.isAdminBranch = true")
    Optional<ClientBranch> findAdminBranch(Integer clientId);
}
