package com.twindigital.auth.service;

import com.twindigital.auth.dto.AuthResponse;
import com.twindigital.auth.dto.LoginRequest;
import com.twindigital.auth.dto.RegisterRequest;
import com.twindigital.auth.entity.RevokedToken;
import com.twindigital.auth.entity.User;
import com.twindigital.auth.repository.RevokedTokenRepository;
import com.twindigital.auth.repository.UserRepository;
import com.twindigital.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

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
        log.info("User logged in: username={} role={}", user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .accessToken(token)
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

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.VIEWER)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: username={}", saved.getUsername());
        return saved;
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
}
