package com.twindigital.clustersync.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KubernetesClientConfig {

    private final ClusterSyncProperties properties;

    @Bean
    public ApiClient apiClient() {
        // 1. Base64-encoded Kubeconfig (common in CI/CD & Docker Compose env vars)
        String base64Config = properties.getKubeconfigBase64();
        if (base64Config == null || base64Config.isBlank()) {
            base64Config = System.getenv("KUBECONFIG_BASE64");
        }
        if (base64Config != null && !base64Config.isBlank()) {
            try {
                log.info("Initializing Kubernetes ApiClient from KUBECONFIG_BASE64");
                byte[] decoded = Base64.getDecoder().decode(base64Config.trim());
                String yaml = new String(decoded, StandardCharsets.UTF_8);
                KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new StringReader(yaml));
                return ClientBuilder.kubeconfig(kubeConfig).build();
            } catch (Exception e) {
                log.warn("Failed to load KubeConfig from base64 env var: {}", e.getMessage());
            }
        }

        // 2. Explicit Kubeconfig file path
        String configPath = properties.getKubeconfigPath();
        if (configPath == null || configPath.isBlank()) {
            configPath = System.getenv("KUBECONFIG");
        }
        if (configPath != null && !configPath.isBlank()) {
            File configFile = new File(configPath);
            if (configFile.exists() && configFile.canRead()) {
                try {
                    log.info("Initializing Kubernetes ApiClient from explicit file: {}", configPath);
                    KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new FileReader(configFile));
                    return ClientBuilder.kubeconfig(kubeConfig).build();
                } catch (Exception e) {
                    log.warn("Failed to load KubeConfig from file {}: {}", configPath, e.getMessage());
                }
            }
        }

        // 3. In-Cluster ServiceAccount (EKS / Kubernetes pod)
        File serviceAccountToken = new File("/var/run/secrets/kubernetes.io/serviceaccount/token");
        if (serviceAccountToken.exists()) {
            try {
                log.info("Initializing Kubernetes ApiClient using in-cluster ServiceAccount token");
                return ClientBuilder.cluster().build();
            } catch (Exception e) {
                log.warn("Failed to load in-cluster Kubernetes config: {}", e.getMessage());
            }
        }

        // 4. Default user file ~/.kube/config (local workstation developer flow)
        File defaultKubeConfig = new File(System.getProperty("user.home"), ".kube/config");
        if (defaultKubeConfig.exists() && defaultKubeConfig.canRead()) {
            try {
                log.info("Initializing Kubernetes ApiClient from ~/.kube/config");
                KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new FileReader(defaultKubeConfig));
                return ClientBuilder.kubeconfig(kubeConfig).build();
            } catch (Exception e) {
                log.warn("Failed to load ~/.kube/config: {}", e.getMessage());
            }
        }

        // 5. Standard fallback
        try {
            log.info("Attempting ClientBuilder.standard()");
            return ClientBuilder.standard().build();
        } catch (Exception e) {
            log.warn("No Kubernetes credentials found. Falling back to default unauthenticated ApiClient: {}", e.getMessage());
            return new ApiClient();
        }
    }
}
