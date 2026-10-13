package com.twindigital.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class JwtTokenProviderTest {

    // 64-char hex secret — safe for HS256
    private static final String SECRET =
            "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20" +
            "2122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 3_600_000L);
    }

    @Test
    @DisplayName("generated token is valid")
    void generatedTokenIsValid() {
        String token = provider.generateToken("alice", "VIEWER");
        assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("extracted username matches subject")
    void extractUsername() {
        String token = provider.generateToken("alice", "VIEWER");
        assertThat(provider.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    @DisplayName("tampered token is not valid")
    void tamperedTokenInvalid() {
        String token = provider.generateToken("alice", "VIEWER");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThat(provider.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("jti is non-null UUID string")
    void jtiPresent() {
        String token = provider.generateToken("alice", "VIEWER");
        assertThatNoException().isThrownBy(() -> {
            String jti = provider.extractJti(token);
            assertThat(jti).isNotBlank();
        });
    }
}
