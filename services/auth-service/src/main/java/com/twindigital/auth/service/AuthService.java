package com.twindigital.auth.service;

import com.twindigital.auth.dto.AuthResponse;
import com.twindigital.auth.dto.LoginRequest;
import com.twindigital.auth.dto.RegisterRequest;
import com.twindigital.auth.entity.RevokedToken;
import com.twindigital.auth.entity.User;
import com.twindigital.auth.repository.RevokedTokenRepository;
import com.twindigital.auth.repository.UserRepository;
import com.twindigital.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // In-memory fallback if Redis is not connected during local dev/unit tests
    private final Map<String, String> localRefreshTokenStore = new ConcurrentHashMap<>();

    @Value("${jwt.expiration-ms:3600000}")
    private long expirationMs;

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final String REDIS_PREFIX = "refresh_token:";

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + request.getUsername()));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());
        String refreshToken = generateAndStoreRefreshToken(user.getUsername());

        log.info("User logged in: username={} role={}", user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expirationMs / 1000)
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        User.Role role = User.Role.VIEWER;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                role = User.Role.valueOf(request.getRole().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid role: " + request.getRole() + ". Must be ADMIN or VIEWER.");
            }
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: username={} role={}", saved.getUsername(), saved.getRole());
        return saved;
    }

    public AuthResponse refresh(String refreshToken) {
        String username = getUsernameByRefreshToken(refreshToken);
        if (username == null) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        // Rotate refresh token
        removeRefreshToken(refreshToken);
        String newRefreshToken = generateAndStoreRefreshToken(username);
        String newAccessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(expirationMs / 1000)
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public void logout(String token) {
        String jti = jwtTokenProvider.extractJti(token);
        OffsetDateTime expiresAt = jwtTokenProvider.extractExpiration(token)
                .toInstant()
                .atOffset(java.time.ZoneOffset.UTC);

        RevokedToken revoked = RevokedToken.builder()
                .jti(jti)
                .expiresAt(expiresAt)
                .build();
        revokedTokenRepository.save(revoked);
        log.info("Token revoked: jti={}", jti);
    }

    public boolean isTokenRevoked(String jti) {
        return revokedTokenRepository.existsByJti(jti);
    }

    public Claims validateToken(String token) {
        if (!jwtTokenProvider.isValid(token)) {
            throw new BadCredentialsException("Invalid or expired token");
        }
        String jti = jwtTokenProvider.extractJti(token);
        if (isTokenRevoked(jti)) {
            throw new BadCredentialsException("Token has been revoked");
        }
        return jwtTokenProvider.validateAndParseClaims(token);
    }

    // ── Redis Helper Methods ───────────────────────────────────────────────────────
    private String generateAndStoreRefreshToken(String username) {
        String refreshToken = UUID.randomUUID().toString();
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(REDIS_PREFIX + refreshToken, username, REFRESH_TOKEN_TTL);
            } else {
                localRefreshTokenStore.put(refreshToken, username);
            }
        } catch (Exception e) {
            log.warn("Redis write failed, falling back to local memory store: {}", e.getMessage());
            localRefreshTokenStore.put(refreshToken, username);
        }
        return refreshToken;
    }

    private String getUsernameByRefreshToken(String refreshToken) {
        try {
            if (redisTemplate != null) {
                String username = redisTemplate.opsForValue().get(REDIS_PREFIX + refreshToken);
                if (username != null) return username;
            }
        } catch (Exception e) {
            log.warn("Redis read failed, trying local memory store: {}", e.getMessage());
        }
        return localRefreshTokenStore.get(refreshToken);
    }

    private void removeRefreshToken(String refreshToken) {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(REDIS_PREFIX + refreshToken);
            }
        } catch (Exception e) {
            log.warn("Redis delete failed: {}", e.getMessage());
        }
        localRefreshTokenStore.remove(refreshToken);
    }
}
