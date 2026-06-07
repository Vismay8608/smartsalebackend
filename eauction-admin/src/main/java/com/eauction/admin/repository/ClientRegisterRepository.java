package com.eauction.admin.repository;

import com.eauction.admin.entity.ClientRegister;
import com.eauction.common.enums.ClientCategory;
import com.eauction.common.enums.ClientType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRegisterRepository extends JpaRepository<ClientRegister, Integer> {

    boolean existsByEmailPrimary(String email);
    boolean existsByMobilePrimary(String mobile);

    Optional<ClientRegister> findByEmailPrimary(String email);

    @Query("SELECT c FROM ClientRegister c WHERE c.clientId = :clientId AND c.clientCategory = :category")
    Optional<ClientRegister> findByIdAndCategory(Integer clientId, ClientCategory category);

    @Query("SELECT COUNT(c) > 0 FROM ClientRegister c WHERE c.emailPrimary = :email AND c.clientCategory = :category AND c.clientType = :type")
    boolean existsByEmailAndCategoryAndType(String email, ClientCategory category, ClientType type);
}
