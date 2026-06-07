package com.eauction.web.security;

import com.eauction.admin.entity.UserRegister;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class AppUserDetails implements UserDetails {

    private final Integer userId;
    private final Integer clientId;
    private final String username;
    private final String password;
    private final String userType;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppUserDetails(UserRegister user, Set<String> permissionCodes) {
        this.userId          = user.getUserId();
        this.clientId        = user.getClient().getClientId();
        this.username        = user.getUsername();
        this.password        = user.getPasswordHash();
        this.userType        = user.getUserType().name();
        this.enabled         = user.isEnabled();
        this.accountNonLocked = !user.isLocked();
        this.authorities     = permissionCodes.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Override public boolean isAccountNonExpired()  { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
