package com.twindigital.clustersync.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ClusterSyncMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> eventCounters = new ConcurrentHashMap<>();
    private final AtomicLong currentLagSeconds = new AtomicLong(0);
    private final AtomicLong isHealthyGaugeValue = new AtomicLong(1);
    private final Counter reconnectCounter;

    public ClusterSyncMetrics(MeterRegistry registry) {
        this.registry = registry;

        Gauge.builder("cluster_sync_lag_seconds", currentLagSeconds, AtomicLong::get)
                .description("Kubernetes cluster watcher sync lag in seconds")
                .register(registry);

        Gauge.builder("cluster_sync_healthy", isHealthyGaugeValue, AtomicLong::get)
                .description("Cluster sync health state: 1 for HEALTHY, 0 for STALE")
                .register(registry);

        this.reconnectCounter = Counter.builder("cluster_sync_reconnect_total")
                .description("Total number of informer reconnection attempts")
                .register(registry);
    }

    /**
     * Increment event counter tagged with kind (node, pod, service, ...) and eventType (ADDED, UPDATED, DELETED).
     */
    public void recordEvent(String kind, String eventType) {
        String key = kind.toLowerCase() + "_" + eventType.toUpperCase();
        eventCounters.computeIfAbsent(key, k -> Counter.builder("cluster_sync_events_processed_total")
                .tag("kind", kind.toLowerCase())
                .tag("event_type", eventType.toUpperCase())
                .description("Total number of Kubernetes watch events processed")
                .register(registry)).increment();
    }

    /**
     * Update current lag gauge in seconds.
     */
    public void updateLagSeconds(long lagSeconds) {
        this.currentLagSeconds.set(Math.max(0, lagSeconds));
    }

    /**
     * Update healthy status gauge: true -> 1, false -> 0.
     */
    public void updateHealthy(boolean healthy) {
        this.isHealthyGaugeValue.set(healthy ? 1 : 0);
    }

    /**
     * Record a reconnection attempt.
     */
    public void recordReconnectAttempt() {
        this.reconnectCounter.increment();
    }

    public long getLagSeconds() {
        return this.currentLagSeconds.get();
    }

    public boolean isHealthy() {
        return this.isHealthyGaugeValue.get() == 1;
    }
}
