package com.twindigital.auth.config;

import com.twindigital.auth.entity.User;
import com.twindigital.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminUserBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new AdminUserBootstrap(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(bootstrap, "adminUsername", "admin");
        ReflectionTestUtils.setField(bootstrap, "adminEmail", "admin@twindigital.local");
    }

    @Test
    @DisplayName("Should require an explicit admin password for every profile")
    void testValidateFailsWhenPasswordIsEmpty() {
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "");

        assertThatThrownBy(() -> bootstrap.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD is required to bootstrap the admin user");
    }

    @Test
    @DisplayName("Should reject whitespace-only admin passwords")
    void testValidateFailsWhenPasswordIsWhitespace() {
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "   ");

        assertThatThrownBy(() -> bootstrap.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD is required to bootstrap the admin user");
    }

    @Test
    @DisplayName("Should succeed when an admin password is provided")
    void testValidateSucceedsWhenPasswordProvided() {
        when(passwordEncoder.encode("SecretPass456!")).thenReturn("encoded-secret");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "SecretPass456!");

        bootstrap.validate();
        bootstrap.run();

        verify(userRepository).save(any(User.class));
    }
}
