package com.twindigital.clustersync.controller;

import com.twindigital.clustersync.config.ClusterSyncProperties;
import com.twindigital.clustersync.model.dto.SyncStatusResponse;
import com.twindigital.clustersync.model.entity.NodeEntity;
import com.twindigital.clustersync.model.entity.PodEntity;
import com.twindigital.clustersync.repository.*;
import com.twindigital.clustersync.service.SyncHealthTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncControllerTest {

    @Mock private SyncHealthTracker healthTracker;
    @Mock private ClusterSyncProperties properties;
    @Mock private NodeRepository nodeRepository;
    @Mock private PodRepository podRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private DeploymentRepository deploymentRepository;
    @Mock private HpaRepository hpaRepository;
    @Mock private PdbRepository pdbRepository;

    @InjectMocks
    private SyncController controller;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getClusterName()).thenReturn("twindigital-cluster");
    }

    @Test
    @DisplayName("Should return 200 with HEALTHY status from /api/sync/status")
    void shouldReturnHealthyStatus() {
        SyncStatusResponse mockResponse = SyncStatusResponse.builder()
                .status("HEALTHY")
                .lagSeconds(2)
                .clusterName("twindigital-cluster")
                .connected(true)
                .lastEventTimestamp(Instant.now())
                .reconnectAttempts(0)
                .build();

        when(healthTracker.buildStatusResponse()).thenReturn(mockResponse);

        ResponseEntity<SyncStatusResponse> response = controller.status();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("HEALTHY", response.getBody().getStatus());
        assertEquals(2, response.getBody().getLagSeconds());
        assertTrue(response.getBody().isConnected());
    }

    @Test
    @DisplayName("Should return 200 with STALE status when connection drops")
    void shouldReturnStaleStatusWhenDropped() {
        SyncStatusResponse mockResponse = SyncStatusResponse.builder()
                .status("STALE")
                .lagSeconds(150)
                .clusterName("twindigital-cluster")
                .connected(false)
                .reconnectAttempts(3)
                .message("Watch connection disconnected: API server EOF")
                .build();

        when(healthTracker.buildStatusResponse()).thenReturn(mockResponse);

        ResponseEntity<SyncStatusResponse> response = controller.status();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("STALE", response.getBody().getStatus());
        assertEquals(150, response.getBody().getLagSeconds());
        assertFalse(response.getBody().isConnected());
        assertEquals(3, response.getBody().getReconnectAttempts());
    }

    @Test
    @DisplayName("Should return list of active pods for cluster")
    void shouldReturnPods() {
        PodEntity mockPod = PodEntity.builder()
                .clusterName("twindigital-cluster")
                .namespace("default")
                .name("test-pod")
                .phase("Running")
                .build();

        when(podRepository.findByClusterName("twindigital-cluster")).thenReturn(List.of(mockPod));

        ResponseEntity<List<PodEntity>> response = controller.getPods(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("test-pod", response.getBody().get(0).getName());
    }
}
