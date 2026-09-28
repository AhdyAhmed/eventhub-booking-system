package com.ahdyahmed.eventhub.auth;

import com.ahdyahmed.eventhub.user.User;
import com.ahdyahmed.eventhub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security's {@code UserDetailsService} contract calls its lookup
 * key "username" unconditionally — this app has no separate username
 * concept, so email fills that role everywhere Spring Security's own API
 * says "username." Backs both the login flow ({@code
 * DaoAuthenticationProvider} calls this to fetch a {@link UserPrincipal} to
 * check the submitted password against) and {@code
 * JwtAuthenticationFilter} (which calls it to re-hydrate a principal from a
 * token's subject claim on every subsequent authenticated request).
 */
@Service
@RequiredArgsConstructor
public class EventHubUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email " + email));
        return new UserPrincipal(user);
    }
}
