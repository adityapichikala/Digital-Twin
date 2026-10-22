package com.twindigital.clustersync.service;

import com.twindigital.clustersync.config.ClusterSyncProperties;
import com.twindigital.clustersync.metrics.ClusterSyncMetrics;
import com.twindigital.clustersync.model.dto.SyncStatusResponse;
import com.twindigital.clustersync.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncHealthTrackerTest {

    private ClusterSyncProperties properties;
    private ClusterSyncMetrics metrics;
    private SyncHealthTracker tracker;

    @Mock private NodeRepository nodeRepository;
    @Mock private PodRepository podRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private DeploymentRepository deploymentRepository;
    @Mock private HpaRepository hpaRepository;
    @Mock private PdbRepository pdbRepository;
    @Mock private SyncStateRepository syncStateRepository;

    @BeforeEach
    void setUp() {
        properties = new ClusterSyncProperties();
        properties.setClusterName("test-eks-cluster");
        properties.setStaleThresholdSeconds(60);
        properties.setReconnectInitialDelaySeconds(1);
        properties.setReconnectMaxDelaySeconds(60);
        properties.setReconnectMultiplier(2.0);

        metrics = new ClusterSyncMetrics(new SimpleMeterRegistry());

        tracker = new SyncHealthTracker(
                properties, metrics,
                nodeRepository, podRepository, serviceRepository,
                deploymentRepository, hpaRepository, pdbRepository,
                syncStateRepository
        );
    }

    @Test
    @DisplayName("Should initially be disconnected and STALE until informers connect")
    void shouldInitiallyBeDisconnected() {
        assertFalse(tracker.isHealthy());
        SyncStatusResponse status = tracker.buildStatusResponse();
        assertEquals("STALE", status.getStatus());
        assertFalse(status.isConnected());
    }

    @Test
    @DisplayName("Should become HEALTHY upon markConnected")
    void shouldBecomeHealthyUponConnect() {
        tracker.markConnected();
        assertTrue(tracker.isHealthy());

        when(nodeRepository.countByClusterName("test-eks-cluster")).thenReturn(3L);
        when(podRepository.countByClusterName("test-eks-cluster")).thenReturn(15L);

        SyncStatusResponse response = tracker.buildStatusResponse();
        assertEquals("HEALTHY", response.getStatus());
        assertTrue(response.isConnected());
        assertEquals("test-eks-cluster", response.getClusterName());
        assertEquals(3L, response.getResourceSyncStatus().get("nodes").getCount());
        assertEquals(15L, response.getResourceSyncStatus().get("pods").getCount());
    }

    @Test
    @DisplayName("Should become STALE when watch connection drops")
    void shouldBecomeStaleWhenConnectionDrops() {
        tracker.markConnected();
        assertTrue(tracker.isHealthy());

        tracker.markDisconnected("Watch stream EOF received from kube-apiserver");

        assertFalse(tracker.isHealthy());
        SyncStatusResponse response = tracker.buildStatusResponse();
        assertEquals("STALE", response.getStatus());
        assertFalse(response.isConnected());
        assertTrue(response.getMessage().contains("Watch connection disconnected"));
    }

    @Test
    @DisplayName("Should record heartbeat and maintain healthy status")
    void shouldRecordHeartbeat() {
        tracker.markDisconnected("Temporary failure");
        assertFalse(tracker.isHealthy());

        tracker.recordHeartbeat();
        assertTrue(tracker.isHealthy());
        assertEquals(0, tracker.calculateLagSeconds());
    }

    @Test
    @DisplayName("Should persist status to td_sync_state")
    void shouldPersistStatusToSyncState() {
        tracker.markConnected();
        when(nodeRepository.countByClusterName("test-eks-cluster")).thenReturn(5L);
        when(podRepository.countByClusterName("test-eks-cluster")).thenReturn(20L);
        when(serviceRepository.countByClusterName("test-eks-cluster")).thenReturn(4L);
        when(deploymentRepository.countByClusterName("test-eks-cluster")).thenReturn(3L);
        when(hpaRepository.countByClusterName("test-eks-cluster")).thenReturn(2L);
        when(pdbRepository.countByClusterName("test-eks-cluster")).thenReturn(1L);
        when(syncStateRepository.findByClusterName("test-eks-cluster")).thenReturn(java.util.Optional.empty());

        tracker.persistSyncState();

        verify(syncStateRepository).save(argThat(entity ->
                "test-eks-cluster".equals(entity.getClusterName()) &&
                "HEALTHY".equals(entity.getStatus()) &&
                entity.isConnected() &&
                entity.getResourceCounts() != null &&
                Long.valueOf(5L).equals(entity.getResourceCounts().get("nodes")) &&
                Long.valueOf(20L).equals(entity.getResourceCounts().get("pods"))
        ));
    }

    @Test
    @DisplayName("Should correctly calculate exponential backoff delay capped at max")
    void shouldCalculateExponentialBackoff() {
        // Attempt 1: 1 * 2^0 = 1s
        assertEquals(1, tracker.computeBackoffDelaySeconds(1));
        // Attempt 2: 1 * 2^1 = 2s
        assertEquals(2, tracker.computeBackoffDelaySeconds(2));
        // Attempt 3: 1 * 2^2 = 4s
        assertEquals(4, tracker.computeBackoffDelaySeconds(3));
        // Attempt 4: 1 * 2^3 = 8s
        assertEquals(8, tracker.computeBackoffDelaySeconds(4));
        // Attempt 5: 1 * 2^4 = 16s
        assertEquals(16, tracker.computeBackoffDelaySeconds(5));
        // Attempt 6: 1 * 2^5 = 32s
        assertEquals(32, tracker.computeBackoffDelaySeconds(6));
        // Attempt 7: 1 * 2^6 = 64s, capped at max (60s)
        assertEquals(60, tracker.computeBackoffDelaySeconds(7));
        // Attempt 10: capped at 60s
        assertEquals(60, tracker.computeBackoffDelaySeconds(10));
    }

    @Test
    @DisplayName("Should increment reconnect attempts and track in status")
    void shouldIncrementReconnectAttempts() {
        assertEquals(1, tracker.incrementReconnectAttempts());
        assertEquals(2, tracker.incrementReconnectAttempts());

        SyncStatusResponse response = tracker.buildStatusResponse();
        assertEquals(2, response.getReconnectAttempts());
        assertEquals(2, response.getCurrentBackoffDelaySeconds());
    }
}
