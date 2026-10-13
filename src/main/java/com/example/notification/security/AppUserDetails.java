package com.example.notification.security;

import com.example.notification.model.AppUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

/**
 * Custom UserDetails that carries the user's database ID,
 * allowing controllers to extract it without an extra DB query.
 */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;

    public AppUserDetails(AppUser user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.isEnabled();
    }

    public Long getId() {
        return id;
    }

    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    @Override
    public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
