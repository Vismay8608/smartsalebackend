package com.eauction.admin.service;

import com.eauction.admin.entity.*;
import com.eauction.admin.repository.*;
import com.eauction.common.enums.AccountStatus;
import com.eauction.common.enums.UserType;
import com.eauction.common.exception.AppException;
import com.eauction.common.response.ResponseCode;
import com.eauction.common.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserProvisioningHelper {

    private final UserRegisterRepository userRegisterRepository;
    private final UserProfileRepository userProfileRepository;
    private final MasterRoleRepository masterRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegister createUser(
            ClientRegister client,
            Integer branchId,
            String username,
            String email,
            String phoneNumber,
            String rawPassword,
            UserType userType,
            Integer createdBy) {

        if (userRegisterRepository.existsByUsername(username)) {
            throw AppException.conflict(ResponseCode.USER_ALREADY_EXISTS);
        }
        if (userRegisterRepository.existsByEmail(email)) {
            throw AppException.conflict(ResponseCode.USER_ALREADY_EXISTS);
        }

        String salt = HashUtil.generateSalt();
        String encodedPassword = passwordEncoder.encode(rawPassword + salt);

        UserRegister user = UserRegister.builder()
                .client(client)
                .branchId(branchId)
                .username(username)
                .email(email)
                .phoneNumber(phoneNumber)
                .passwordHash(encodedPassword)
                .passwordSalt(salt)
                .userType(userType)
                .accountStatus(AccountStatus.ACTIVE)
                .isActive(true)
                .forcePasswordChange(true)
                .passwordChangedAt(OffsetDateTime.now())
                .createdBy(createdBy)
                .build();

        return userRegisterRepository.save(user);
    }

    public UserProfile createProfile(UserRegister user, String firstName, String middleName,
                                     String lastName, String designation, String employeeCode, Integer createdBy) {
        UserProfile profile = UserProfile.builder()
                .user(user)
                .firstName(firstName)
                .middleName(middleName)
                .lastName(lastName)
                .designation(designation)
                .employeeCode(employeeCode)
                .updatedBy(createdBy)
                .build();
        return userProfileRepository.save(profile);
    }

    public void assignRole(UserRegister user, MasterRole role, ClientRegister client,
                            Integer branchId, boolean isPrimary, Integer assignedBy) {
        boolean alreadyAssigned = userRoleRepository.existsByUserUserIdAndRoleRoleIdAndStatus(
                user.getUserId(), role.getRoleId(), "ACTIVE");
        if (alreadyAssigned) return;

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .client(client)
                .branchId(branchId)
                .isPrimary(isPrimary)
                .isBranchScoped(branchId != null)
                .status("ACTIVE")
                .assignedBy(assignedBy)
                .build();
        userRoleRepository.save(userRole);
    }

    public MasterRole resolveRole(Integer clientId, String roleCode) {
        return masterRoleRepository.findByClientClientIdAndRoleCode(clientId, roleCode)
                .orElseThrow(() -> AppException.notFound(ResponseCode.ROLE_NOT_FOUND));
    }

    public Set<String> loadPermissions(Integer userId) {
        List<Integer> roleIds = userRoleRepository.findActiveRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) return Set.of();
        return rolePermissionRepository.findPermissionCodesByRoleIds(roleIds);
    }

    public MasterRole cloneRoleFromTemplate(ClientRegister client, MasterRoleTemplate template, Integer createdBy) {
        String roleCode = template.getTemplateCode() + "_" + client.getClientId();
        return masterRoleRepository.findByClientClientIdAndRoleCode(client.getClientId(), roleCode)
                .orElseGet(() -> {
                    MasterRole role = MasterRole.builder()
                            .client(client)
                            .template(template)
                            .roleCode(roleCode)
                            .roleName(template.getTemplateName())
                            .description(template.getDescription())
                            .actorType(template.getActorType())
                            .clientType(template.getClientType())
                            .source("SYSTEM_TEMPLATE")
                            .isSystem(false)
                            .isActive(true)
                            .status("ACTIVE")
                            .displayOrder(template.getDisplayOrder())
                            .createdBy(createdBy)
                            .build();
                    MasterRole saved = masterRoleRepository.save(role);

                    // Copy permissions from template to role
                    List<RolePermission> rps = template.getPermissions().stream()
                            .map(perm -> RolePermission.builder()
                                    .role(saved)
                                    .permission(perm)
                                    .grantedBy(createdBy)
                                    .build())
                            .collect(Collectors.toList());
                    rolePermissionRepository.saveAll(rps);
                    return saved;
                });
    }
}
