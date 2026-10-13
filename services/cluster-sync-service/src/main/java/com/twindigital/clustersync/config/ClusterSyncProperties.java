package com.twindigital.clustersync.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cluster.sync")
@Getter
@Setter
public class ClusterSyncProperties {

    /**
     * Name of the Kubernetes / EKS cluster being mirrored.
     */
    private String clusterName = "twindigital-cluster";

    /**
     * AWS region of the cluster.
     */
    private String region = "ap-south-1";

    /**
     * Explicit path to local kubeconfig file.
     */
    private String kubeconfigPath;

    /**
     * Base64 encoded kubeconfig string.
     */
    private String kubeconfigBase64;

    /**
     * Duration in seconds without events or heartbeat before data is flagged as STALE.
     */
    private long staleThresholdSeconds = 120;

    /**
     * Initial reconnection delay in seconds for exponential backoff.
     */
    private long reconnectInitialDelaySeconds = 1;

    /**
     * Maximum reconnection delay in seconds for exponential backoff.
     */
    private long reconnectMaxDelaySeconds = 60;

    /**
     * Multiplier for exponential backoff calculation.
     */
    private double reconnectMultiplier = 2.0;

    /**
     * Whether to start informers automatically upon application startup.
     */
    private boolean autoStart = true;
}
