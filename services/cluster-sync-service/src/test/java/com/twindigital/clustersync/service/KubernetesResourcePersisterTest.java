package com.twindigital.clustersync.service;

import com.twindigital.clustersync.model.entity.DeploymentEntity;
import com.twindigital.clustersync.model.entity.ServiceEntity;
import com.twindigital.clustersync.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KubernetesResourcePersisterTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private PodRepository podRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private DeploymentRepository deploymentRepository;
    @Mock private HpaRepository hpaRepository;
    @Mock private PdbRepository pdbRepository;
    @Mock private SyncStateRepository syncStateRepository;

    @InjectMocks
    private KubernetesResourcePersister persister;

    private final String clusterName = "test-cluster";

    @Test
    @DisplayName("Should delete stale resources across all 6 repositories older than sync start")
    void shouldDeleteStaleResources() {
        Instant syncStart = Instant.now().minusSeconds(10);

        persister.deleteStaleResources(clusterName, syncStart);

        verify(nodeRepository).deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        verify(podRepository).deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        verify(serviceRepository).deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        verify(deploymentRepository).deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        verify(hpaRepository).deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        verify(pdbRepository).deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
    }

    @Test
    @DisplayName("Should persist deployment with annotations")
    void shouldPersistDeploymentWithAnnotations() {
        DeploymentEntity dep = DeploymentEntity.builder()
                .clusterName(clusterName)
                .namespace("default")
                .name("web")
                .replicas(2)
                .annotations(Map.of("twindigital.io/depends-on", "api"))
                .build();

        when(deploymentRepository.findByClusterNameAndNamespaceAndName(clusterName, "default", "web"))
                .thenReturn(Optional.empty());

        persister.persistDeployment(dep);

        verify(deploymentRepository).save(argThat(d ->
                "web".equals(d.getName()) &&
                d.getAnnotations() != null &&
                "api".equals(d.getAnnotations().get("twindigital.io/depends-on"))
        ));
    }
}
