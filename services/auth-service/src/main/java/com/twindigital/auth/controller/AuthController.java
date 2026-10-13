package com.twindigital.auth.controller;

import com.twindigital.auth.dto.AuthResponse;
import com.twindigital.auth.dto.LoginRequest;
import com.twindigital.auth.dto.RegisterRequest;
import com.twindigital.auth.entity.User;
import com.twindigital.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping({"/api/v1/auth/login", "/api/auth/login"})
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping({"/api/v1/auth/register", "/api/auth/register"})
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole(),
                "email", user.getEmail()
        ));
    }

    @PostMapping({"/api/v1/auth/refresh", "/api/auth/refresh"})
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (!StringUtils.hasText(refreshToken)) {
            throw new BadCredentialsException("refreshToken is required");
        }
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }

    @PostMapping({"/api/v1/auth/logout", "/api/auth/logout"})
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid Authorization header"));
        }
        authService.logout(authHeader.substring(7));
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Endpoint to validate a JWT: /api/auth/validate and /api/v1/auth/validate.
     * Returns 200 with claims if valid; 401 with consistent error JSON if invalid or expired.
     */
    @GetMapping({"/api/v1/auth/validate", "/api/auth/validate", "/validate"})
    public ResponseEntity<Map<String, Object>> validate(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "valid", false,
                    "error", "Unauthorized",
                    "message", "Missing or malformed Authorization header"
            ));
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = authService.validateToken(token);
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "username", claims.getSubject(),
                    "role", claims.get("role", String.class),
                    "expiresAt", claims.getExpiration().toInstant().toString()
            ));
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "valid", false,
                    "error", "Unauthorized",
                    "message", e.getMessage()
            ));
        }
    }
}
