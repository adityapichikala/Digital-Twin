package com.twindigital.topology.service;

import com.twindigital.topology.entity.*;
import com.twindigital.topology.model.TopologyDTOs.BlastRadiusResponse;
import com.twindigital.topology.model.TopologyDTOs.TopologyNode;
import com.twindigital.topology.model.TopologyDTOs.TopologyResponse;
import com.twindigital.topology.repository.TopologyRepositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopologyGraphServiceTest {

    @Mock
    private ClusterNodeRepository nodeRepository;

    @Mock
    private ClusterPodRepository podRepository;

    @Mock
    private ClusterDeploymentRepository deploymentRepository;

    @Mock
    private ClusterServiceRepository serviceRepository;

    @Mock
    private ClusterSyncStateRepository syncStateRepository;

    @Mock
    private ServiceDependencyRepository dependencyRepository;

    @InjectMocks
    private TopologyGraphService topologyGraphService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Should return default seed topology when database has no synced records")
    void shouldReturnDefaultTopologyWhenDatabaseEmpty() {
        when(nodeRepository.findByClusterName(anyString())).thenReturn(Collections.emptyList());
        when(podRepository.findByClusterName(anyString())).thenReturn(Collections.emptyList());
        when(deploymentRepository.findByClusterName(anyString())).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(anyString())).thenReturn(Collections.emptyList());
        when(syncStateRepository.findByClusterName(anyString())).thenReturn(Optional.empty());

        TopologyResponse response = topologyGraphService.getTopology("twindigital-cluster");

        assertThat(response).isNotNull();
        assertThat(response.getNodes()).isNotEmpty();
        assertThat(response.getEdges()).isNotEmpty();
        assertThat(response.getHealth()).isNotNull();
        assertThat(response.getStaleness()).isNotNull();

        // Verify Node > Pod > Deployment > Service nodes exist in seed topology
        boolean hasNode = response.getNodes().stream().anyMatch(n -> "NODE".equals(n.getType()));
        boolean hasPod = response.getNodes().stream().anyMatch(n -> "POD".equals(n.getType()));
        boolean hasDeployment = response.getNodes().stream().anyMatch(n -> "DEPLOYMENT".equals(n.getType()));
        boolean hasService = response.getNodes().stream().anyMatch(n -> "SERVICE".equals(n.getType()));

        assertThat(hasNode).isTrue();
        assertThat(hasPod).isTrue();
        assertThat(hasDeployment).isTrue();
        assertThat(hasService).isTrue();

        // Verify dependency edges exist
        boolean hasDependencyEdges = response.getEdges().stream()
                .anyMatch(e -> "DEPENDENCY".equals(e.getType()) || "INGRESS".equals(e.getType()));
        assertThat(hasDependencyEdges).isTrue();
    }

    @Test
    @DisplayName("Should build graph from synced entities including depends-on annotation")
    void shouldBuildGraphFromEntitiesWithAnnotations() {
        String cluster = "twindigital-cluster";

        ClusterNodeEntity node = ClusterNodeEntity.builder()
                .clusterName(cluster)
                .name("ip-10-0-1-10.ec2.internal")
                .ready(true)
                .allocatableCpu("2000m")
                .allocatableMemory("4Gi")
                .status("Ready")
                .build();

        ClusterDeploymentEntity authDep = ClusterDeploymentEntity.builder()
                .clusterName(cluster)
                .namespace("twindigital")
                .name("auth-service")
                .replicas(2)
                .readyReplicas(2)
                .labels(Map.of("app", "auth-service"))
                .build();

        ClusterDeploymentEntity billingDep = ClusterDeploymentEntity.builder()
                .clusterName(cluster)
                .namespace("twindigital")
                .name("billing-service")
                .replicas(2)
                .readyReplicas(2)
                .labels(Map.of("app", "billing-service", "twindigital.io/depends-on", "auth-service"))
                .build();

        ClusterPodEntity pod = ClusterPodEntity.builder()
                .clusterName(cluster)
                .namespace("twindigital")
                .name("billing-service-7bc89d-abc12")
                .nodeName("ip-10-0-1-10.ec2.internal")
                .ownerKind("Deployment")
                .ownerName("billing-service")
                .phase("Running")
                .restarts(0)
                .cpuRequests("100m")
                .memoryRequests("256Mi")
                .build();

        ClusterServiceEntity billingSvc = ClusterServiceEntity.builder()
                .clusterName(cluster)
                .namespace("twindigital")
                .name("billing-service")
                .serviceType("ClusterIP")
                .clusterIp("172.20.10.1")
                .selector(Map.of("app", "billing-service"))
                .build();

        ClusterSyncStateEntity syncState = ClusterSyncStateEntity.builder()
                .clusterName(cluster)
                .status("HEALTHY")
                .connected(true)
                .lagSeconds(0)
                .lastEventAt(Instant.now())
                .build();

        when(nodeRepository.findByClusterName(cluster)).thenReturn(List.of(node));
        when(podRepository.findByClusterName(cluster)).thenReturn(List.of(pod));
        when(deploymentRepository.findByClusterName(cluster)).thenReturn(List.of(authDep, billingDep));
        when(serviceRepository.findByClusterName(cluster)).thenReturn(List.of(billingSvc));
        when(syncStateRepository.findByClusterName(cluster)).thenReturn(Optional.of(syncState));
        when(dependencyRepository.findByClusterName(cluster)).thenReturn(Collections.emptyList());

        TopologyResponse response = topologyGraphService.getTopology(cluster);

        assertThat(response).isNotNull();
        assertThat(response.getNodes()).hasSize(4); // 1 node, 2 deployments, 1 pod, 1 svc

        // Check Node -> Pod edge
        boolean nodeToPodEdge = response.getEdges().stream()
                .anyMatch(e -> e.getSource().equals("node:ip-10-0-1-10.ec2.internal") &&
                               e.getTarget().equals("pod:twindigital/billing-service-7bc89d-abc12"));
        assertThat(nodeToPodEdge).isTrue();

        // Check twindigital.io/depends-on edge: billing-service -> auth-service
        boolean dependsOnEdge = response.getEdges().stream()
                .anyMatch(e -> e.getSource().equals("service:twindigital/billing-service") &&
                               e.getTarget().equals("service:twindigital/auth-service") &&
                               "DEPENDENCY".equals(e.getType()));
        assertThat(dependsOnEdge).isTrue();

        assertThat(response.getStaleness().isStale()).isFalse();
    }

    @Test
    @DisplayName("Should calculate blast radius correctly returning upstream callers and downstream dependencies")
    void shouldCalculateBlastRadius() {
        when(nodeRepository.findByClusterName(anyString())).thenReturn(Collections.emptyList());
        when(podRepository.findByClusterName(anyString())).thenReturn(Collections.emptyList());
        when(deploymentRepository.findByClusterName(anyString())).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(anyString())).thenReturn(Collections.emptyList());
        when(syncStateRepository.findByClusterName(anyString())).thenReturn(Optional.empty());

        // Target auth-service in seed topology
        BlastRadiusResponse response = topologyGraphService.calculateBlastRadius("twindigital-cluster", "auth-service");

        assertThat(response).isNotNull();
        assertThat(response.getTarget()).isNotNull();
        assertThat(response.getTarget().getName()).contains("auth");

        // Auth service is called by gateway, topology-service, cluster-sync-service, etc.
        assertThat(response.getUpstream()).isNotEmpty();
        assertThat(response.getImpactedNodeIds()).isNotEmpty();
        assertThat(response.getImpactedEdgeIds()).isNotEmpty();
        assertThat(response.getRiskScore()).isEqualTo("CRITICAL");
        assertThat(response.getExplanation()).contains("blast radius");
    }

    @Test
    @DisplayName("Should reject blast radius calculation with empty target")
    void shouldThrowWhenTargetIsBlank() {
        assertThatThrownBy(() -> topologyGraphService.calculateBlastRadius("cluster", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
