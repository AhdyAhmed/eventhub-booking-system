package com.ahdyahmed.eventhub.config;

import com.ahdyahmed.eventhub.auth.EventHubUserDetailsService;
import com.ahdyahmed.eventhub.auth.JwtAuthenticationEntryPoint;
import com.ahdyahmed.eventhub.auth.JwtAuthenticationFilter;
import com.ahdyahmed.eventhub.auth.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * "Minimal JWT auth," per the roadmap's own words — deliberately not a
 * roles/permissions system. Every authenticated user has exactly the same
 * capabilities; the only per-user restriction anywhere in this project is
 * booking ownership ({@code BookingServiceImpl}'s {@code requireOwnership},
 * checked in the service layer against the specific resource being touched,
 * not expressible as a static URL-pattern rule here). A real production
 * system would very likely add roles (an admin who can manage venues and
 * events, say) — out of scope for what this day's roadmap line actually
 * asks for, and flagged here rather than silently absent.
 *
 * <p><strong>What's public vs. authenticated:</strong> browsing is public
 * (every {@code GET} under {@code /api/v1/events/**} and {@code
 * /api/v1/venues/**} — a ticketing site's whole point is that you can look
 * before you log in), registration and login are public by necessity, and
 * {@code /actuator/health/**} stays public for infrastructure liveness/
 * readiness probes that have no way to carry a bearer token. Everything
 * else — creating/updating/deleting events or seats, every booking
 * endpoint, {@code /actuator/info} and {@code /actuator/metrics/**} — needs
 * a valid token. That last part is this day's other roadmap line: actuator
 * exposed, but "not wide open."</p>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(EventHubUserDetailsService userDetailsService,
                                                           PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     AuthenticationProvider authenticationProvider,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter,
                                                     JwtAuthenticationEntryPoint authenticationEntryPoint)
            throws Exception {
        http
                // A stateless, token-per-request API has no session cookie for
                // CSRF to forge in the first place - CSRF protection defends
                // browser session-cookie auth specifically, which this app
                // doesn't use.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/**", "/api/v1/venues/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
