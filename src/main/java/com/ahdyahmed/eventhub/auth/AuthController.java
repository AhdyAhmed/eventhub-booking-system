package com.ahdyahmed.eventhub.auth;

import com.ahdyahmed.eventhub.auth.dto.AuthResponse;
import com.ahdyahmed.eventhub.auth.dto.LoginRequest;
import com.ahdyahmed.eventhub.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only two endpoints in this project reachable with no {@code
 * Authorization} header at all — {@code SecurityConfig} permits {@code
 * /api/v1/auth/**} unconditionally, for the obvious reason that a request
 * for a token can't itself require one.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register or exchange credentials for a bearer token")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a user", description = "Creates an account and immediately returns a JWT for protected operations.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns a JWT for an existing email/password pair.")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
