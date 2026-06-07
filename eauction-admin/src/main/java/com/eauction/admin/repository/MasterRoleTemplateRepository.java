package com.eauction.admin.repository;

import com.eauction.admin.entity.MasterRoleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasterRoleTemplateRepository extends JpaRepository<MasterRoleTemplate, Integer> {
    Optional<MasterRoleTemplate> findByTemplateCode(String templateCode);
    List<MasterRoleTemplate> findByActorTypeAndIsActiveTrueOrderByDisplayOrderAsc(String actorType);
    List<MasterRoleTemplate> findByActorTypeAndClientTypeAndIsActiveTrueOrderByDisplayOrderAsc(String actorType, String clientType);
}
