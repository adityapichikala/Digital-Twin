package com.twindigital.clustersync.service;

import com.twindigital.clustersync.config.ClusterSyncProperties;
import com.twindigital.clustersync.metrics.ClusterSyncMetrics;
import com.twindigital.clustersync.model.dto.ResourceSyncSummary;
import com.twindigital.clustersync.model.dto.SyncStatusResponse;
import com.twindigital.clustersync.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncHealthTracker {

    private final ClusterSyncProperties properties;
    private final ClusterSyncMetrics metrics;
    private final NodeRepository nodeRepository;
    private final PodRepository podRepository;
    private final ServiceRepository serviceRepository;
    private final DeploymentRepository deploymentRepository;
    private final HpaRepository hpaRepository;
    private final PdbRepository pdbRepository;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastEventTime = new AtomicReference<>(Instant.now());
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicReference<String> lastErrorMessage = new AtomicReference<>(null);

    private final Map<String, Instant> resourceLastEvent = new ConcurrentHashMap<>();
    private final Map<String, Boolean> resourceSynced = new ConcurrentHashMap<>();

    public void markConnected() {
        this.connected.set(true);
        this.reconnectAttempts.set(0);
        this.lastErrorMessage.set(null);
        this.lastEventTime.set(Instant.now());
        metrics.updateHealthy(true);
        log.info("Watch connection established for cluster '{}'", properties.getClusterName());
    }

    public void markDisconnected(String reason) {
        this.connected.set(false);
        this.lastErrorMessage.set(reason);
        metrics.updateHealthy(false);
        log.warn("Watch connection dropped for cluster '{}': {}. Marking state as STALE.", properties.getClusterName(), reason);
    }

    public void recordEvent(String kind) {
        Instant now = Instant.now();
        this.lastEventTime.set(now);
        this.resourceLastEvent.put(kind.toLowerCase(), now);
        this.connected.set(true);

        long lag = 0;
        metrics.updateLagSeconds(lag);
        metrics.updateHealthy(true);
    }

    public void markResourceSynced(String kind, boolean synced) {
        this.resourceSynced.put(kind.toLowerCase(), synced);
    }

    public long calculateLagSeconds() {
        Instant last = lastEventTime.get();
        if (last == null) return 0;
        long lag = Math.max(0, Duration.between(last, Instant.now()).getSeconds());
        metrics.updateLagSeconds(lag);
        return lag;
    }

    public boolean isHealthy() {
        if (!connected.get()) {
            return false;
        }
        long lag = calculateLagSeconds();
        boolean healthy = lag <= properties.getStaleThresholdSeconds();
        metrics.updateHealthy(healthy);
        return healthy;
    }

    public int incrementReconnectAttempts() {
        int attempts = reconnectAttempts.incrementAndGet();
        metrics.recordReconnectAttempt();
        return attempts;
    }

    public long computeBackoffDelaySeconds(int attempt) {
        double delay = properties.getReconnectInitialDelaySeconds() *
                Math.pow(properties.getReconnectMultiplier(), Math.max(0, attempt - 1));
        return (long) Math.min(properties.getReconnectMaxDelaySeconds(), delay);
    }

    public SyncStatusResponse buildStatusResponse() {
        boolean healthy = isHealthy();
        long lag = calculateLagSeconds();

        String status = healthy ? "HEALTHY" : "STALE";

        String clusterName = properties.getClusterName();

        Map<String, ResourceSyncSummary> summaryMap = Map.of(
                "nodes", ResourceSyncSummary.builder()
                        .synced(resourceSynced.getOrDefault("nodes", false))
                        .count(nodeRepository.countByClusterName(clusterName))
                        .lastEventAt(resourceLastEvent.get("nodes"))
                        .build(),
                "pods", ResourceSyncSummary.builder()
                        .synced(resourceSynced.getOrDefault("pods", false))
                        .count(podRepository.countByClusterName(clusterName))
                        .lastEventAt(resourceLastEvent.get("pods"))
                        .build(),
                "services", ResourceSyncSummary.builder()
                        .synced(resourceSynced.getOrDefault("services", false))
                        .count(serviceRepository.countByClusterName(clusterName))
                        .lastEventAt(resourceLastEvent.get("services"))
                        .build(),
                "deployments", ResourceSyncSummary.builder()
                        .synced(resourceSynced.getOrDefault("deployments", false))
                        .count(deploymentRepository.countByClusterName(clusterName))
                        .lastEventAt(resourceLastEvent.get("deployments"))
                        .build(),
                "hpas", ResourceSyncSummary.builder()
                        .synced(resourceSynced.getOrDefault("hpas", false))
                        .count(hpaRepository.countByClusterName(clusterName))
                        .lastEventAt(resourceLastEvent.get("hpas"))
                        .build(),
                "pdbs", ResourceSyncSummary.builder()
                        .synced(resourceSynced.getOrDefault("pdbs", false))
                        .count(pdbRepository.countByClusterName(clusterName))
                        .lastEventAt(resourceLastEvent.get("pdbs"))
                        .build()
        );

        int attempts = reconnectAttempts.get();
        long currentBackoff = attempts > 0 ? computeBackoffDelaySeconds(attempts) : 0;

        return SyncStatusResponse.builder()
                .status(status)
                .lagSeconds(lag)
                .clusterName(clusterName)
                .connected(connected.get())
                .lastEventTimestamp(lastEventTime.get())
                .reconnectAttempts(attempts)
                .currentBackoffDelaySeconds(currentBackoff)
                .resourceSyncStatus(summaryMap)
                .message(connected.get()
                        ? (healthy ? "Informers active and synchronized with cluster" : "Sync lag exceeded threshold (" + properties.getStaleThresholdSeconds() + "s)")
                        : ("Watch connection disconnected: " + (lastErrorMessage.get() != null ? lastErrorMessage.get() : "Connection dropped")))
                .build();
    }
}
