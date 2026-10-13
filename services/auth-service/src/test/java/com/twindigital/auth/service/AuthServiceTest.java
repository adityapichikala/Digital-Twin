package com.twindigital.auth.service;

import com.twindigital.auth.dto.LoginRequest;
import com.twindigital.auth.dto.RegisterRequest;
import com.twindigital.auth.entity.User;
import com.twindigital.auth.repository.RevokedTokenRepository;
import com.twindigital.auth.repository.UserRepository;
import com.twindigital.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RevokedTokenRepository revokedTokenRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks AuthService authService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(authService, "expirationMs", 3600000L);
    }

    @Test
    @DisplayName("login returns token when credentials are valid")
    void loginSuccess() {
        var user = User.builder()
                .username("alice")
                .password("hashed")
                .role(User.Role.VIEWER)
                .enabled(true)
                .build();

        var request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateToken("alice", "VIEWER")).thenReturn("tok");

        var resp = authService.login(request);
        assertThat(resp.getAccessToken()).isEqualTo("tok");
        assertThat(resp.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("login throws BadCredentialsException for wrong password")
    void loginBadPassword() {
        var user = User.builder()
                .username("alice")
                .password("hashed")
                .role(User.Role.VIEWER)
                .enabled(true)
                .build();

        var request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("register throws when username is already taken")
    void registerDuplicateUsername() {
        var request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("Password1");

        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already taken");
    }

    @Test
    @DisplayName("register saves user with VIEWER role")
    void registerSuccess() {
        var request = new RegisterRequest();
        request.setUsername("bob");
        request.setEmail("bob@example.com");
        request.setPassword("Password1");

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed_bob");

        var saved = User.builder()
                .username("bob")
                .email("bob@example.com")
                .password("hashed_bob")
                .role(User.Role.VIEWER)
                .enabled(true)
                .build();
        when(userRepository.save(any())).thenReturn(saved);

        User result = authService.register(request);
        assertThat(result.getRole()).isEqualTo(User.Role.VIEWER);
        verify(userRepository).save(any());
    }
}
