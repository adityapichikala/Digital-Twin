package com.twindigital.auth.config;

import com.twindigital.auth.entity.User;
import com.twindigital.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Environment environment;

    private AdminUserBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new AdminUserBootstrap(userRepository, passwordEncoder, environment);
        ReflectionTestUtils.setField(bootstrap, "adminUsername", "admin");
        ReflectionTestUtils.setField(bootstrap, "adminEmail", "admin@twindigital.local");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when profile is not local and password is empty")
    void testValidateFailsWhenNotLocalAndNoPassword() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "");

        assertThatThrownBy(() -> bootstrap.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD is required when active profile is not 'local'");
    }

    @Test
    @DisplayName("Should use default password when profile is local and password is empty")
    void testValidateSucceedsWhenLocalAndNoPassword() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(passwordEncoder.encode("AdminPassword123!")).thenReturn("encoded-pass");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "");

        bootstrap.validate();
        bootstrap.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getPassword()).isEqualTo("encoded-pass");
        assertThat(saved.getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    @DisplayName("Should succeed when profile is prod and password is provided")
    void testValidateSucceedsWhenPasswordProvided() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(passwordEncoder.encode("SecretPass456!")).thenReturn("encoded-secret");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "SecretPass456!");

        bootstrap.validate();
        bootstrap.run();

        verify(userRepository).save(any(User.class));
    }
}
