package com.twindigital.clustersync.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClusterSyncMetricsTest {

    private MeterRegistry meterRegistry;
    private ClusterSyncMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ClusterSyncMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Should track events processed counter by kind and event_type")
    void shouldTrackEventsProcessedCounter() {
        metrics.recordEvent("pod", "ADDED");
        metrics.recordEvent("pod", "ADDED");
        metrics.recordEvent("pod", "DELETED");
        metrics.recordEvent("node", "UPDATED");

        Counter podAdded = meterRegistry.find("cluster_sync_events_processed_total")
                .tag("kind", "pod")
                .tag("event_type", "ADDED")
                .counter();
        assertNotNull(podAdded);
        assertEquals(2.0, podAdded.count());

        Counter podDeleted = meterRegistry.find("cluster_sync_events_processed_total")
                .tag("kind", "pod")
                .tag("event_type", "DELETED")
                .counter();
        assertNotNull(podDeleted);
        assertEquals(1.0, podDeleted.count());

        Counter nodeUpdated = meterRegistry.find("cluster_sync_events_processed_total")
                .tag("kind", "node")
                .tag("event_type", "UPDATED")
                .counter();
        assertNotNull(nodeUpdated);
        assertEquals(1.0, nodeUpdated.count());
    }

    @Test
    @DisplayName("Should update sync lag gauge in seconds")
    void shouldUpdateSyncLagGauge() {
        metrics.updateLagSeconds(42);

        Gauge lagGauge = meterRegistry.find("cluster_sync_lag_seconds").gauge();
        assertNotNull(lagGauge);
        assertEquals(42.0, lagGauge.value());

        metrics.updateLagSeconds(0);
        assertEquals(0.0, lagGauge.value());
    }

    @Test
    @DisplayName("Should update health status gauge (1 for HEALTHY, 0 for STALE)")
    void shouldUpdateHealthStatusGauge() {
        Gauge healthGauge = meterRegistry.find("cluster_sync_healthy").gauge();
        assertNotNull(healthGauge);

        // Initially healthy (1)
        assertEquals(1.0, healthGauge.value());

        // Marked STALE (0)
        metrics.updateHealthy(false);
        assertEquals(0.0, healthGauge.value());

        // Reconnected to HEALTHY (1)
        metrics.updateHealthy(true);
        assertEquals(1.0, healthGauge.value());
    }

    @Test
    @DisplayName("Should record reconnect attempts counter")
    void shouldRecordReconnectAttempts() {
        metrics.recordReconnectAttempt();
        metrics.recordReconnectAttempt();

        Counter reconnect = meterRegistry.find("cluster_sync_reconnect_total").counter();
        assertNotNull(reconnect);
        assertEquals(2.0, reconnect.count());
    }
}
