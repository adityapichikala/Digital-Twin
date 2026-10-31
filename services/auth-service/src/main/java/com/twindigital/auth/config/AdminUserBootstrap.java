package com.twindigital.auth.config;

import com.twindigital.auth.entity.User;
import com.twindigital.auth.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Bootstrap component to ensure the seed admin user is created/updated from environment variables.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${twindigital.bootstrap.admin.username:admin}")
    private String adminUsername;

    @Value("${twindigital.bootstrap.admin.password:}")
    private String adminPassword;

    @Value("${twindigital.bootstrap.admin.email:admin@twindigital.local}")
    private String adminEmail;

    @PostConstruct
    public void validate() {
        boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains("local");
        if (isLocal && (adminPassword == null || adminPassword.trim().isEmpty())) {
            adminPassword = "AdminPassword123!";
        } else if (!isLocal && (adminPassword == null || adminPassword.trim().isEmpty())) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD is required when active profile is not 'local'");
        }
    }

    @Override
    public void run(String... args) {
        userRepository.findByUsername(adminUsername).ifPresentOrElse(
            user -> {
                log.info("Bootstrap admin user '{}' is present", adminUsername);
            },
            () -> {
                log.info("Initializing bootstrap admin user '{}'", adminUsername);
                User admin = User.builder()
                        .username(adminUsername)
                        .email(adminEmail)
                        .password(passwordEncoder.encode(adminPassword))
                        .role(User.Role.ADMIN)
                        .enabled(true)
                        .build();
                userRepository.save(admin);
                log.info("Bootstrap admin user '{}' created successfully", adminUsername);
            }
        );
    }
}
