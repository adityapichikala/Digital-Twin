package com.twindigital.auth.config;

import com.twindigital.auth.entity.User;
import com.twindigital.auth.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstrap component to ensure the seed admin user is created/updated from environment variables.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${twindigital.bootstrap.admin.username:admin}")
    private String adminUsername;

    @Value("${twindigital.bootstrap.admin.password:}")
    private String adminPassword;

    @Value("${twindigital.bootstrap.admin.email:admin@twindigital.local}")
    private String adminEmail;

    @PostConstruct
    public void validate() {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD is required to bootstrap the admin user");
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
