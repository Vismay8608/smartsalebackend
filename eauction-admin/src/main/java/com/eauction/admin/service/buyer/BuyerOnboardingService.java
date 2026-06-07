package com.eauction.admin.service.buyer;

import com.eauction.admin.dto.buyer.*;
import com.eauction.admin.entity.*;
import com.eauction.admin.repository.*;
import com.eauction.admin.service.UserProvisioningHelper;
import com.eauction.common.enums.*;
import com.eauction.common.exception.AppException;
import com.eauction.common.response.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuyerOnboardingService {

    private final ClientRegisterRepository      clientRegisterRepository;
    private final ClientCompanyProfileRepository companyProfileRepository;
    private final ClientBranchLevelRepository   branchLevelRepository;
    private final ClientBranchRepository        branchRepository;
    private final MasterRoleTemplateRepository  roleTemplateRepository;
    private final UserProvisioningHelper        provisioningHelper;

    // ── Company buyer: Step 1 – register ─────────────────────────────────────

    @Transactional
    public Map<String, Object> registerCompanyBuyer(BuyerCompanyRegisterRequest req) {
        log.info("Registering company buyer [email={}]", req.emailPrimary());
        guardDuplicateEmail(req.emailPrimary(), ClientCategory.BUYER, ClientType.COMPANY);

        ClientRegister client = ClientRegister.builder()
                .clientCategory(ClientCategory.BUYER)
                .clientType(ClientType.COMPANY)
                .emailPrimary(req.emailPrimary())
                .mobilePrimary(req.mobilePrimary())
                .addressLine1(req.addressLine1())
                .addressLine2(req.addressLine2())
                .city(req.city())
                .state(req.state())
                .pincode(req.pincode())
                .registrationStatus(RegistrationStatus.REGISTRATION_STARTED)
                .accountStatus(AccountStatus.INACTIVE)
                .build();
        clientRegisterRepository.save(client);

        ClientCompanyProfile profile = ClientCompanyProfile.builder()
                .client(client)
                .companyName(req.companyName())
                .companyNameShort(req.companyNameShort())
                .companyType(req.companyType())
                .build();
        companyProfileRepository.save(profile);

        // Provision system branch and buyer admin role in one shot
        ClientBranchLevel level = branchLevelRepository.save(ClientBranchLevel.builder()
                .client(client).levelCode("HO").label("Head Office")
                .levelOrder((short) 1).isAdminBranch(true).isActive(true).build());

        branchRepository.save(ClientBranch.builder()
                .client(client).level(level)
                .branchCode("HO-B-" + client.getClientId())
                .branchName("Head Office").isSystemGenerated(true).build());

        // Clone buyer_admin role from template
        List<MasterRoleTemplate> templates = roleTemplateRepository
                .findByActorTypeAndClientTypeAndIsActiveTrueOrderByDisplayOrderAsc("BUYER", "COMPANY");
        templates.forEach(t -> provisioningHelper.cloneRoleFromTemplate(client, t, null));

        return Map.of(
                "clientId",  client.getClientId(),
                "status",    "REGISTRATION_STARTED",
                "nextStep",  "Create authorised user at /onboarding/buyers/company/{clientId}/users"
        );
    }

    // ── Company buyer: Step 2 – single authorised user ────────────────────────

    @Transactional
    public Map<String, Object> createCompanyBuyerUser(Integer clientId, BuyerCompanyUserRequest req, Integer createdBy) {
        ClientRegister client = resolveBuyerClient(clientId, ClientType.COMPANY);

        ClientBranch rootBranch = branchRepository.findRootBranch(clientId)
                .orElseThrow(() -> AppException.internalError(ResponseCode.INTERNAL_ERROR));

        String adminRoleCode = "BUYER_ADMIN_" + clientId;

        UserRegister user = provisioningHelper.createUser(
                client, rootBranch.getBranchId(),
                req.username(), req.email(), req.phoneNumber(),
                req.password(), UserType.BUYER, createdBy);

        provisioningHelper.createProfile(user,
                req.firstName(), req.middleName(), req.lastName(),
                req.designation(), null, createdBy);

        MasterRole role = provisioningHelper.resolveRole(clientId, adminRoleCode);
        provisioningHelper.assignRole(user, role, client, rootBranch.getBranchId(), true, createdBy);

        client.setAdminUserId(user.getUserId());
        client.setAdminUsername(user.getUsername());
        client.setRegistrationStatus(RegistrationStatus.KYC_PENDING);
        clientRegisterRepository.save(client);

        return Map.of(
                "clientId", clientId,
                "userId",   user.getUserId(),
                "username", user.getUsername(),
                "status",   "KYC_PENDING",
                "nextStep", "Submit KYC for verification"
        );
    }

    // ── Individual buyer: single step ─────────────────────────────────────────

    @Transactional
    public Map<String, Object> registerIndividualBuyer(BuyerIndividualRegisterRequest req) {
        log.info("Registering individual buyer [email={}]", req.email());
        guardDuplicateEmail(req.email(), ClientCategory.BUYER, ClientType.INDIVIDUAL);

        ClientRegister client = ClientRegister.builder()
                .clientCategory(ClientCategory.BUYER)
                .clientType(ClientType.INDIVIDUAL)
                .emailPrimary(req.email())
                .mobilePrimary(req.mobilePrimary())
                .addressLine1(req.addressLine1())
                .city(req.city()).state(req.state()).pincode(req.pincode())
                .registrationStatus(RegistrationStatus.KYC_PENDING)
                .accountStatus(AccountStatus.INACTIVE)
                .build();
        clientRegisterRepository.save(client);

        ClientBranchLevel level = branchLevelRepository.save(ClientBranchLevel.builder()
                .client(client).levelCode("SELF").label("Self")
                .levelOrder((short) 1).isAdminBranch(true).isActive(true).build());

        ClientBranch branch = branchRepository.save(ClientBranch.builder()
                .client(client).level(level)
                .branchCode("SELF-B-" + client.getClientId())
                .branchName("Self Branch").isSystemGenerated(true).build());

        List<MasterRoleTemplate> templates = roleTemplateRepository
                .findByActorTypeAndClientTypeAndIsActiveTrueOrderByDisplayOrderAsc("BUYER", "INDIVIDUAL");
        MasterRoleTemplate template = templates.stream().findFirst()
                .orElseThrow(() -> AppException.internalError(ResponseCode.INTERNAL_ERROR));
        MasterRole role = provisioningHelper.cloneRoleFromTemplate(client, template, null);

        UserRegister user = provisioningHelper.createUser(
                client, branch.getBranchId(),
                req.username(), req.email(), req.mobilePrimary(),
                req.password(), UserType.BUYER, null);

        provisioningHelper.createProfile(user,
                req.firstName(), req.middleName(), req.lastName(),
                null, null, null);

        provisioningHelper.assignRole(user, role, client, branch.getBranchId(), true, null);

        client.setAdminUserId(user.getUserId());
        client.setAdminUsername(user.getUsername());
        clientRegisterRepository.save(client);

        log.info("Individual buyer registered [clientId={}, userId={}]", client.getClientId(), user.getUserId());
        return Map.of(
                "clientId", client.getClientId(),
                "userId",   user.getUserId(),
                "username", user.getUsername(),
                "status",   "KYC_PENDING",
                "nextStep", "Submit KYC documents"
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOnboardingStatus(Integer clientId) {
        ClientRegister client = resolveBuyerClient(clientId, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientId",           client.getClientId());
        m.put("clientType",         client.getClientType());
        m.put("registrationStatus", client.getRegistrationStatus());
        m.put("accountStatus",      client.getAccountStatus());
        m.put("emailPrimary",       client.getEmailPrimary());
        m.put("adminUsername",      client.getAdminUsername());
        return m;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ClientRegister resolveBuyerClient(Integer clientId, ClientType expectedType) {
        ClientRegister c = clientRegisterRepository.findById(clientId)
                .filter(x -> x.getClientCategory() == ClientCategory.BUYER)
                .orElseThrow(() -> AppException.notFound(ResponseCode.CLIENT_NOT_FOUND));
        if (expectedType != null && c.getClientType() != expectedType) {
            throw AppException.badRequest(ResponseCode.INVALID_INPUT);
        }
        return c;
    }

    private void guardDuplicateEmail(String email, ClientCategory category, ClientType type) {
        if (clientRegisterRepository.existsByEmailAndCategoryAndType(email, category, type)) {
            throw AppException.conflict(ResponseCode.CLIENT_ALREADY_EXISTS);
        }
    }
}
