package com.eauction.web.service;

import com.eauction.admin.entity.UserRegister;
import com.eauction.admin.repository.UserRegisterRepository;
import com.eauction.admin.repository.UserRoleRepository;
import com.eauction.admin.repository.RolePermissionRepository;
import com.eauction.web.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRegisterRepository  userRegisterRepository;
    private final UserRoleRepository      userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        UserRegister user = userRegisterRepository.findByUsernameOrEmail(identifier)
                .orElseThrow(() -> {
                    log.warn("User not found for identifier: {}", identifier);
                    return new UsernameNotFoundException("User not found: " + identifier);
                });

        List<Integer> roleIds = userRoleRepository.findActiveRoleIdsByUserId(user.getUserId());
        Set<String> permissions = roleIds.isEmpty()
                ? Set.of()
                : rolePermissionRepository.findPermissionCodesByRoleIds(roleIds);

        return new AppUserDetails(user, permissions);
    }
}
