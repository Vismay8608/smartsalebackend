package com.eauction.admin.service.seller;

import com.eauction.admin.dto.seller.*;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerOnboardingService {

    private final ClientRegisterRepository         clientRegisterRepository;
    private final ClientCompanyProfileRepository   companyProfileRepository;
    private final ClientBranchLevelRepository      branchLevelRepository;
    private final ClientBranchRepository           branchRepository;
    private final MasterRoleTemplateRepository     roleTemplateRepository;
    private final UserProvisioningHelper            provisioningHelper;

    // ── Step 1: Company seller – register client ──────────────────────────────

    @Transactional
    public Map<String, Object> registerCompanySeller(SellerCompanyRegisterRequest req) {
        log.info("Registering company seller [email={}]", req.emailPrimary());

        guardDuplicateEmail(req.emailPrimary(), ClientCategory.SELLER, ClientType.COMPANY);

        ClientRegister client = ClientRegister.builder()
                .clientCategory(ClientCategory.SELLER)
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

        ClientRegister saved = clientRegisterRepository.save(client);

        ClientCompanyProfile profile = ClientCompanyProfile.builder()
                .client(saved)
                .companyName(req.companyName())
                .companyNameShort(req.companyNameShort())
                .companyType(req.companyType())
                .build();
        companyProfileRepository.save(profile);

        log.info("Company seller registered [clientId={}]", saved.getClientId());
        return Map.of(
                "clientId",           saved.getClientId(),
                "registrationStatus", saved.getRegistrationStatus(),
                "nextStep",           "Complete company profile at /onboarding/sellers/company/{clientId}/profile"
        );
    }

    // ── Step 2: Company seller – add full company profile ────────────────────

    @Transactional
    public Map<String, Object> updateCompanyProfile(Integer clientId, SellerCompanyProfileRequest req, Integer updatedBy) {
        ClientRegister client = resolveSellerClient(clientId, ClientType.COMPANY);

        ClientCompanyProfile profile = companyProfileRepository.findByClientClientId(clientId)
                .orElseGet(() -> ClientCompanyProfile.builder().client(client).build());

        profile.setCompanyLegalName(req.companyLegalName());
        profile.setWebsiteUrl(req.websiteUrl());
        profile.setEstablishmentDate(req.establishmentDate());
        profile.setIncorporationDate(req.incorporationDate());
        profile.setNumberOfEmployees(req.numberOfEmployees());
        profile.setAnnualTurnover(req.annualTurnover());
        profile.setBusinessCategory(req.businessCategory());
        profile.setIndustryType(req.industryType());
        profile.setClassification(req.classification());
        profile.setMobileSecondary(req.mobileSecondary());
        profile.setEmailSecondary(req.emailSecondary());
        profile.setSupportEmail(req.supportEmail());
        profile.setFaxNumber(req.faxNumber());
        profile.setUpdatedBy(updatedBy);
        companyProfileRepository.save(profile);

        client.setRegistrationStatus(RegistrationStatus.KYC_PENDING);
        clientRegisterRepository.save(client);

        return Map.of(
                "clientId",    clientId,
                "status",      "PROFILE_COMPLETED",
                "nextStep",    "Setup branch levels at /onboarding/sellers/company/{clientId}/branches/levels"
        );
    }

    // ── Step 3: Create branch hierarchy levels ────────────────────────────────

    @Transactional
    public Map<String, Object> createBranchLevels(Integer clientId, SellerBranchLevelRequest req, Integer createdBy) {
        ClientRegister client = resolveSellerClient(clientId, ClientType.COMPANY);

        // Admin (root) level is always level_order = 1 and is system-generated
        ClientBranchLevel adminLevel = ClientBranchLevel.builder()
                .client(client)
                .levelCode("HO")
                .label("Head Office")
                .levelOrder((short) 1)
                .isAdminBranch(true)
                .isActive(true)
                .createdBy(createdBy)
                .build();
        branchLevelRepository.save(adminLevel);

        // Client-defined sub-levels start from order 2
        List<ClientBranchLevel> userLevels = req.levels().stream()
                .map(l -> ClientBranchLevel.builder()
                        .client(client)
                        .levelCode(l.levelCode())
                        .label(l.label())
                        .levelOrder(l.levelOrder())
                        .isAdminBranch(false)
                        .isActive(true)
                        .createdBy(createdBy)
                        .build())
                .collect(Collectors.toList());
        branchLevelRepository.saveAll(userLevels);

        // Create the root (admin) branch automatically
        ClientBranch rootBranch = ClientBranch.builder()
                .client(client)
                .level(adminLevel)
                .branchCode("HO-" + clientId)
                .branchName("Head Office")
                .isSystemGenerated(true)
                .createdBy(createdBy)
                .build();
        branchRepository.save(rootBranch);

        return Map.of(
                "clientId",    clientId,
                "rootBranchId", rootBranch.getBranchId(),
                "nextStep",    "Create roles at /onboarding/sellers/company/{clientId}/roles"
        );
    }

    // ── Step 4: Create branch ─────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createBranch(Integer clientId, SellerBranchRequest req, Integer createdBy) {
        ClientRegister client = resolveSellerClient(clientId, null);

        if (branchRepository.existsByBranchCode(req.branchCode())) {
            throw AppException.conflict(ResponseCode.BRANCH_ALREADY_EXISTS);
        }

        ClientBranchLevel level = branchLevelRepository.findById(req.levelId())
                .orElseThrow(() -> AppException.notFound(ResponseCode.BRANCH_NOT_FOUND));

        ClientBranch parent = req.parentBranchId() != null
                ? branchRepository.findById(req.parentBranchId()).orElse(null)
                : null;

        ClientBranch branch = ClientBranch.builder()
                .client(client)
                .level(level)
                .parent(parent)
                .branchCode(req.branchCode())
                .branchName(req.branchName())
                .branchCity(req.branchCity())
                .branchState(req.branchState())
                .branchDistrict(req.branchDistrict())
                .branchCountry(req.branchCountry() != null ? req.branchCountry() : "India")
                .branchAddress(req.branchAddress())
                .pinCode(req.pinCode())
                .phonePrimary(req.phonePrimary())
                .emailPrimary(req.emailPrimary())
                .status("ACTIVE")
                .isSystemGenerated(false)
                .createdBy(createdBy)
                .build();

        ClientBranch saved = branchRepository.save(branch);
        return Map.of("branchId", saved.getBranchId(), "branchCode", saved.getBranchCode());
    }

    // ── Step 5: Provision default roles from templates ────────────────────────

    @Transactional
    public Map<String, Object> provisionDefaultRoles(Integer clientId, Integer createdBy) {
        ClientRegister client = resolveSellerClient(clientId, ClientType.COMPANY);

        List<MasterRoleTemplate> templates = roleTemplateRepository
                .findByActorTypeAndClientTypeAndIsActiveTrueOrderByDisplayOrderAsc("SELLER", "COMPANY");

        List<String> roleCodes = templates.stream()
                .map(t -> provisioningHelper.cloneRoleFromTemplate(client, t, createdBy).getRoleCode())
                .collect(Collectors.toList());

        return Map.of(
                "clientId",   clientId,
                "rolesCreated", roleCodes,
                "nextStep",   "Create admin user at /onboarding/sellers/company/{clientId}/users"
        );
    }

    // ── Step 6: Create seller company user ────────────────────────────────────

    @Transactional
    public Map<String, Object> createSellerUser(Integer clientId, SellerUserCreateRequest req, Integer createdBy) {
        ClientRegister client = resolveSellerClient(clientId, null);

        UserRegister user = provisioningHelper.createUser(
                client,
                req.branchId(),
                req.username(), req.email(), req.phoneNumber(),
                req.password(), UserType.SELLER, createdBy);

        provisioningHelper.createProfile(user,
                req.firstName(), req.middleName(), req.lastName(),
                req.designation(), req.employeeCode(), createdBy);

        MasterRole role = provisioningHelper.resolveRole(clientId, req.roleCode());
        provisioningHelper.assignRole(user, role, client, req.branchId(), true, createdBy);

        // First user created = admin user for the client
        if (client.getAdminUserId() == null) {
            client.setAdminUserId(user.getUserId());
            client.setAdminUsername(user.getUsername());
            client.setRegistrationStatus(RegistrationStatus.ONBOARDING_COMPLETE);
            client.setAccountStatus(AccountStatus.ACTIVE);
            clientRegisterRepository.save(client);
        }

        return Map.of(
                "userId",    user.getUserId(),
                "username",  user.getUsername(),
                "clientId",  clientId,
                "status",    "ONBOARDING_COMPLETE"
        );
    }

    // ── Individual seller onboarding (single step) ────────────────────────────

    @Transactional
    public Map<String, Object> registerIndividualSeller(SellerIndividualRegisterRequest req) {
        log.info("Registering individual seller [email={}]", req.email());
        guardDuplicateEmail(req.email(), ClientCategory.SELLER, ClientType.INDIVIDUAL);

        ClientRegister client = ClientRegister.builder()
                .clientCategory(ClientCategory.SELLER)
                .clientType(ClientType.INDIVIDUAL)
                .emailPrimary(req.email())
                .mobilePrimary(req.mobilePrimary())
                .addressLine1(req.addressLine1())
                .addressLine2(req.addressLine2())
                .city(req.city())
                .state(req.state())
                .pincode(req.pincode())
                .registrationStatus(RegistrationStatus.KYC_PENDING)
                .accountStatus(AccountStatus.INACTIVE)
                .build();
        clientRegisterRepository.save(client);

        // Create self-referencing root branch level and branch for individual
        ClientBranchLevel level = ClientBranchLevel.builder()
                .client(client)
                .levelCode("SELF")
                .label("Self")
                .levelOrder((short) 1)
                .isAdminBranch(true)
                .isActive(true)
                .build();
        branchLevelRepository.save(level);

        ClientBranch branch = ClientBranch.builder()
                .client(client)
                .level(level)
                .branchCode("SELF-" + client.getClientId())
                .branchName("Self Branch")
                .isSystemGenerated(true)
                .build();
        branchRepository.save(branch);

        // Provision individual seller role from template
        List<MasterRoleTemplate> templates = roleTemplateRepository
                .findByActorTypeAndClientTypeAndIsActiveTrueOrderByDisplayOrderAsc("SELLER", "INDIVIDUAL");
        MasterRoleTemplate template = templates.stream().findFirst()
                .orElseThrow(() -> AppException.internalError(ResponseCode.INTERNAL_ERROR));
        MasterRole role = provisioningHelper.cloneRoleFromTemplate(client, template, null);

        UserRegister user = provisioningHelper.createUser(
                client, branch.getBranchId(),
                req.username(), req.email(), req.mobilePrimary(),
                req.password(), UserType.SELLER, null);

        provisioningHelper.createProfile(user,
                req.firstName(), req.middleName(), req.lastName(),
                null, null, null);

        provisioningHelper.assignRole(user, role, client, branch.getBranchId(), true, null);

        client.setAdminUserId(user.getUserId());
        client.setAdminUsername(user.getUsername());
        clientRegisterRepository.save(client);

        log.info("Individual seller registered [clientId={}, userId={}]", client.getClientId(), user.getUserId());
        return Map.of(
                "clientId",  client.getClientId(),
                "userId",    user.getUserId(),
                "username",  user.getUsername(),
                "status",    "KYC_PENDING",
                "nextStep",  "Submit KYC documents"
        );
    }

    // ── Onboarding status ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getOnboardingStatus(Integer clientId) {
        ClientRegister client = resolveSellerClient(clientId, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientId",           client.getClientId());
        m.put("clientType",         client.getClientType());
        m.put("registrationStatus", client.getRegistrationStatus());
        m.put("accountStatus",      client.getAccountStatus());
        m.put("emailPrimary",       client.getEmailPrimary());
        m.put("adminUsername",      client.getAdminUsername());
        return m;
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    private ClientRegister resolveSellerClient(Integer clientId, ClientType expectedType) {
        ClientRegister c = clientRegisterRepository.findById(clientId)
                .filter(x -> x.getClientCategory() == ClientCategory.SELLER)
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
