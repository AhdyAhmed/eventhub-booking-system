package com.ahdyahmed.eventhub.auth;

import com.ahdyahmed.eventhub.auth.dto.AuthResponse;
import com.ahdyahmed.eventhub.auth.dto.LoginRequest;
import com.ahdyahmed.eventhub.auth.dto.RegisterRequest;
import com.ahdyahmed.eventhub.user.User;
import com.ahdyahmed.eventhub.user.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place in this project that's allowed to turn a raw password into
 * a stored {@link User} or check one against a stored hash — everywhere
 * else, "the current user" means a {@link UserPrincipal} already resolved
 * from a validated token, never a password in transit.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // No pre-check on whether the email already exists: that check
        // would itself be a race (two concurrent registrations for the same
        // email could both pass a SELECT-based check before either INSERTs)
        // - the same reasoning BookingServiceImpl's seat pre-check plus
        // @Version double-checks, applied here via the database's own
        // UNIQUE constraint on users.email instead of optimistic locking.
        // A violation surfaces as DataIntegrityViolationException, already
        // mapped to a clean 409 by GlobalExceptionHandler since Day 5.
        User user = User.builder()
                .fullName(request.fullName())
                .email(normalizeEmail(request.email()))
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        User saved = userRepository.save(user);

        String token = jwtService.generateToken(new UserPrincipal(saved));
        return new AuthResponse(token, saved.getId(), saved.getFullName(), saved.getEmail());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // Delegating to AuthenticationManager (backed by the
        // DaoAuthenticationProvider SecurityConfig wires up) rather than
        // manually calling userRepository.findByEmail + passwordEncoder.matches
        // here means both authentication paths - this one, and
        // JwtAuthenticationFilter validating a token on every later request -
        // go through Spring Security's own well-tested provider, not two
        // independent, potentially-diverging implementations of "check a
        // password."
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);
        return new AuthResponse(token, principal.getId(), principal.getFullName(), principal.getUsername());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
