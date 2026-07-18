package com.linman.account.security;

import com.linman.account.entity.Role;
import com.linman.account.entity.User;
import com.linman.account.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {
    private final Long userId;
    private final String username;
    private final String password;
    private final Role role;
    private final UserStatus status;
    private final List<GrantedAuthority> authorities;

    private CustomUserDetails(Long userId, String username, String password, Role role, UserStatus status) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.role = role;
        this.status = status;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(user.getId(), user.getUsername(),
                user.getPasswordHash(), user.getRole(), user.getStatus());
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status == UserStatus.ENABLED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ENABLED;
    }
}
