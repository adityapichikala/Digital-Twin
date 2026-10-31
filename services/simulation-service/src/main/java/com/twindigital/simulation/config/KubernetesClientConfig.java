package com.twindigital.simulation.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Provides in-cluster Kubernetes ApiClient configuration for live dry-run validation.
 */
@Slf4j
@Configuration
public class KubernetesClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public ApiClient apiClient() {
        try {
            // Check for in-cluster ServiceAccount token
            if (new File("/var/run/secrets/kubernetes.io/serviceaccount/token").exists()) {
                log.info("Configuring in-cluster Kubernetes ApiClient for simulation-service");
                return ClientBuilder.cluster().build();
            }
            log.info("No in-cluster ServiceAccount found; ApiClient bean will not be initialized (offline mode)");
            return null;
        } catch (Exception e) {
            log.warn("Failed to initialize in-cluster ApiClient: {}", e.getMessage());
            return null;
        }
    }
}
