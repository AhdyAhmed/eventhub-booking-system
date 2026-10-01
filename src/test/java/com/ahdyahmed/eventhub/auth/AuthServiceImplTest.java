package com.ahdyahmed.eventhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ahdyahmed.eventhub.auth.dto.AuthResponse;
import com.ahdyahmed.eventhub.auth.dto.LoginRequest;
import com.ahdyahmed.eventhub.auth.dto.RegisterRequest;
import com.ahdyahmed.eventhub.user.User;
import com.ahdyahmed.eventhub.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@code AuthenticationManager} and {@code PasswordEncoder} are mocked
 * here, not the real {@code BCryptPasswordEncoder} / {@code
 * DaoAuthenticationProvider} beans — this class exists to prove
 * {@code AuthServiceImpl} calls the right collaborators with the right
 * arguments and maps their results correctly, not to re-prove Spring
 * Security's own password-hashing or credential-checking logic, which
 * isn't this project's code to begin with.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    private final JwtService jwtService = new JwtService(
            new JwtProperties("test-only-signing-secret-at-least-32-bytes-long-xxxxxxxxxxxxxxxx", 60_000L));

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void register_happyPath_hashesPasswordAndReturnsToken() {
        when(passwordEncoder.encode("plaintext-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(1L);
            return user;
        });

        AuthResponse response = authService.register(
                new RegisterRequest("New User", "new-user@example.com", "plaintext-password"));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("new-user@example.com");
        assertThat(response.token()).isNotBlank();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        // The raw password never reaches the saved entity - only what
        // passwordEncoder.encode() returned does.
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    void register_duplicateEmail_propagatesDataIntegrityViolation() {
        // Not caught and translated inside AuthServiceImpl - GlobalExceptionHandler
        // has mapped this to a clean 409 since Day 5, and duplicating that
        // mapping here would be two places deciding the same thing.
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("New User", "taken@example.com", "plaintext-password")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void register_normalizesEmailBeforePersistingAndIssuingToken() {
        when(passwordEncoder.encode("plaintext-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(1L);
            return user;
        });

        AuthResponse response = authService.register(
                new RegisterRequest("New User", "  New-User@Example.COM  ", "plaintext-password"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new-user@example.com");
        assertThat(response.email()).isEqualTo("new-user@example.com");
    }

    @Test
    void login_happyPath_returnsTokenForAuthenticatedPrincipal() {
        User user = User.builder().id(1L).fullName("Existing User").email("existing@example.com")
                .passwordHash("hashed-password").build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(user), null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        AuthResponse response = authService.login(new LoginRequest("existing@example.com", "correct-password"));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("existing@example.com");
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void login_badCredentials_propagatesAuthenticationException() {
        // Same reasoning as the duplicate-email case above: GlobalExceptionHandler
        // maps AuthenticationException to a generic 401 message, deliberately
        // not distinguishing "wrong password" from "no such user" - see that
        // handler's own doc for why collapsing the two is a security choice,
        // not an oversight.
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_normalizesEmailBeforeAuthentication() {
        User user = User.builder().id(1L).fullName("Existing User").email("existing@example.com")
                .passwordHash("hashed-password").build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        authService.login(new LoginRequest("  Existing@Example.COM ", "correct-password"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("existing@example.com");
    }
}
