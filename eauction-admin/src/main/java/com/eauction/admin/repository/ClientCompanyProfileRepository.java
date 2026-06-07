package com.eauction.admin.repository;

import com.eauction.admin.entity.ClientCompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientCompanyProfileRepository extends JpaRepository<ClientCompanyProfile, Integer> {
    Optional<ClientCompanyProfile> findByClientClientId(Integer clientId);
    boolean existsByClientClientId(Integer clientId);
}
