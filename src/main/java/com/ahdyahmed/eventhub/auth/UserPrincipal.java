package com.ahdyahmed.eventhub.auth;

import com.ahdyahmed.eventhub.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The bridge between this app's own {@link User} entity and Spring
 * Security's {@link UserDetails} contract — everything Spring Security's
 * machinery (the {@code AuthenticationProvider}, {@code
 * @AuthenticationPrincipal}, this project's own {@code JwtService}) touches
 * is this wrapper, never the raw entity, so a controller reading {@code
 * @AuthenticationPrincipal UserPrincipal} never accidentally pulls in JPA
 * lazy-loading concerns for an entity that's already detached from its
 * persistence context by request-handling time.
 *
 * <p>{@code getAuthorities()} returns an empty list, not {@code
 * List.of(new SimpleGrantedAuthority("ROLE_USER"))} or similar — see {@code
 * SecurityConfig}'s class doc for why a roles system doesn't exist yet in
 * this project, and why that's a deliberate scope line rather than
 * something forgotten here.</p>
 */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String fullName;
    private final String email;
    private final String passwordHash;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
