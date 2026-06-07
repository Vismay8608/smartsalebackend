package com.eauction.admin.service.system;

import com.eauction.admin.dto.system.CreateSystemUserRequest;
import com.eauction.admin.entity.*;
import com.eauction.admin.repository.*;
import com.eauction.admin.service.UserProvisioningHelper;
import com.eauction.common.enums.AccountStatus;
import com.eauction.common.enums.ClientCategory;
import com.eauction.common.enums.RegistrationStatus;
import com.eauction.common.enums.UserType;
import com.eauction.common.exception.AppException;
import com.eauction.common.response.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemUserService {

    private final ClientRegisterRepository clientRegisterRepository;
    private final UserRegisterRepository userRegisterRepository;
    private final UserProvisioningHelper provisioningHelper;

    /**
     * Creates a system-type user under the platform's own SYSTEM client.
     * Called only by SUPER_ADMIN.
     */
    @Transactional
    public Map<String, Object> createSystemUser(CreateSystemUserRequest req, Integer createdBy) {
        log.info("Creating system user [username={}] by admin [{}]", req.username(), createdBy);

        ClientRegister systemClient = resolveSystemClient();

        UserRegister user = provisioningHelper.createUser(
                systemClient,
                req.branchId(),
                req.username(),
                req.email(),
                req.phoneNumber(),
                req.password(),
                UserType.SYSTEM,
                createdBy);

        provisioningHelper.createProfile(user,
                req.firstName(), req.middleName(), req.lastName(),
                req.designation(), req.employeeCode(), createdBy);

        MasterRole role = provisioningHelper.resolveRole(systemClient.getClientId(), req.roleCode());
        provisioningHelper.assignRole(user, role, systemClient, req.branchId(), true, createdBy);

        log.info("System user created [userId={}, username={}]", user.getUserId(), user.getUsername());

        return buildUserSummary(user);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listSystemUsers(Pageable pageable) {
        ClientRegister systemClient = resolveSystemClient();
        return userRegisterRepository
                .findByClientClientIdAndUserType(systemClient.getClientId(), UserType.SYSTEM, pageable)
                .map(this::buildUserSummary);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSystemUser(Integer userId) {
        UserRegister user = userRegisterRepository.findById(userId)
                .filter(u -> u.getUserType() == UserType.SYSTEM)
                .orElseThrow(() -> AppException.notFound(ResponseCode.USER_NOT_FOUND));
        return buildUserSummary(user);
    }

    @Transactional
    public void updateStatus(Integer userId, AccountStatus status, Integer updatedBy) {
        UserRegister user = userRegisterRepository.findById(userId)
                .filter(u -> u.getUserType() == UserType.SYSTEM)
                .orElseThrow(() -> AppException.notFound(ResponseCode.USER_NOT_FOUND));
        user.setAccountStatus(status);
        user.setIsActive(status == AccountStatus.ACTIVE);
        user.setUpdatedBy(updatedBy);
        userRegisterRepository.save(user);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ClientRegister resolveSystemClient() {
        return clientRegisterRepository
                .findAll()
                .stream()
                .filter(c -> c.getClientCategory() == ClientCategory.SYSTEM
                        && c.getRegistrationStatus() == RegistrationStatus.ONBOARDING_COMPLETE)
                .findFirst()
                .orElseThrow(() -> AppException.internalError(ResponseCode.INTERNAL_ERROR));
    }

    private Map<String, Object> buildUserSummary(UserRegister u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId",        u.getUserId());
        m.put("username",      u.getUsername());
        m.put("email",         u.getEmail());
        m.put("phoneNumber",   u.getPhoneNumber());
        m.put("userType",      u.getUserType());
        m.put("accountStatus", u.getAccountStatus());
        m.put("createdAt",     u.getCreatedAt());
        return m;
    }
}
