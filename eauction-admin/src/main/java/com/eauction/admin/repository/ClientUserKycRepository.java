package com.eauction.admin.repository;

import com.eauction.admin.entity.ClientUserKyc;
import com.eauction.common.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientUserKycRepository extends JpaRepository<ClientUserKyc, Integer> {

    Page<ClientUserKyc> findByKycStatus(KycStatus kycStatus, Pageable pageable);

    Page<ClientUserKyc> findByClientClientIdAndKycStatus(Integer clientId, KycStatus kycStatus, Pageable pageable);

    Page<ClientUserKyc> findByClientClientId(Integer clientId, Pageable pageable);

    Optional<ClientUserKyc> findTopByClientClientIdAndKycSubjectAndUserIdOrderBySubmittedAtDesc(
            Integer clientId, String kycSubject, Integer userId);
}
