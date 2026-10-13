package com.twindigital.auth.service;

import com.twindigital.auth.dto.LoginRequest;
import com.twindigital.auth.dto.RegisterRequest;
import com.twindigital.auth.entity.User;
import com.twindigital.auth.repository.RevokedTokenRepository;
import com.twindigital.auth.repository.UserRepository;
import com.twindigital.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
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
    @DisplayName("login returns access and refresh tokens when credentials are valid")
    void loginSuccess() {
        var user = User.builder()
                .username("alice")
                .password("hashed")
                .role(User.Role.ADMIN)
                .enabled(true)
                .build();

        var request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateToken("alice", "ADMIN")).thenReturn("tok_admin");

        var resp = authService.login(request);
        assertThat(resp.getAccessToken()).isEqualTo("tok_admin");
        assertThat(resp.getUsername()).isEqualTo("alice");
        assertThat(resp.getRole()).isEqualTo("ADMIN");
        assertThat(resp.getRefreshToken()).isNotBlank();
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
    @DisplayName("register saves user with VIEWER role by default")
    void registerViewerDefault() {
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

    @Test
    @DisplayName("register saves user with ADMIN role when specified")
    void registerAdminSuccess() {
        var request = new RegisterRequest();
        request.setUsername("adminuser");
        request.setEmail("admin@example.com");
        request.setPassword("AdminPassword1");
        request.setRole("ADMIN");

        when(userRepository.existsByUsername("adminuser")).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("AdminPassword1")).thenReturn("hashed_admin");

        var saved = User.builder()
                .username("adminuser")
                .email("admin@example.com")
                .password("hashed_admin")
                .role(User.Role.ADMIN)
                .enabled(true)
                .build();
        when(userRepository.save(any())).thenReturn(saved);

        User result = authService.register(request);
        assertThat(result.getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    @DisplayName("validateToken throws when token is invalid or revoked")
    void validateTokenRevokedOrInvalid() {
        when(jwtTokenProvider.isValid("invalid_tok")).thenReturn(false);

        assertThatThrownBy(() -> authService.validateToken("invalid_tok"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid or expired");

        when(jwtTokenProvider.isValid("revoked_tok")).thenReturn(true);
        when(jwtTokenProvider.extractJti("revoked_tok")).thenReturn("jti_123");
        when(revokedTokenRepository.existsByJti("jti_123")).thenReturn(true);

        assertThatThrownBy(() -> authService.validateToken("revoked_tok"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("validateToken returns claims when token is valid and not revoked")
    void validateTokenSuccess() {
        Claims mockClaims = mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("alice");
        when(jwtTokenProvider.isValid("valid_tok")).thenReturn(true);
        when(jwtTokenProvider.extractJti("valid_tok")).thenReturn("jti_456");
        when(revokedTokenRepository.existsByJti("jti_456")).thenReturn(false);
        when(jwtTokenProvider.validateAndParseClaims("valid_tok")).thenReturn(mockClaims);

        Claims result = authService.validateToken("valid_tok");
        assertThat(result.getSubject()).isEqualTo("alice");
    }
}
