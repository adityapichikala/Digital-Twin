package com.twindigital.simulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twindigital.simulation.check.*;
import com.twindigital.simulation.context.SimulationContext.BlastRadiusInfo;
import com.twindigital.simulation.entity.SimulationEntities.*;
import com.twindigital.simulation.model.SimulationModels.*;
import com.twindigital.simulation.repository.SimulationRepositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationBddScenariosTest {

    @Mock
    private ClusterNodeRepository nodeRepository;

    @Mock
    private ClusterPodRepository podRepository;

    @Mock
    private ClusterDeploymentRepository deploymentRepository;

    @Mock
    private ClusterHpaRepository hpaRepository;

    @Mock
    private ClusterPdbRepository pdbRepository;

    @Mock
    private ClusterServiceRepository serviceRepository;

    @Mock
    private SimulationAuditRepository auditRepository;

    @Spy
    private SchedulingFitCheck schedulingFitCheck = new SchedulingFitCheck();

    @Spy
    private LimitSanityCheck limitSanityCheck = new LimitSanityCheck();

    @Spy
    private NodeRemovalImpactCheck nodeRemovalImpactCheck = new NodeRemovalImpactCheck();

    @Spy
    private HpaImpactCheck hpaImpactCheck = new HpaImpactCheck();

    @Spy
    private BlastRadiusCheck blastRadiusCheck = new BlastRadiusCheck();

    @Spy
    private ServerDryRunCheck serverDryRunCheck = new ServerDryRunCheck();

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SimulationEngine simulationEngine;

    private static final String CLUSTER = "twindigital-cluster";

    // Standard baseline 2-node cluster
    private ClusterNodeEntity node1;
    private ClusterNodeEntity node2;

    @BeforeEach
    void initClusterState() {
        node1 = ClusterNodeEntity.builder()
                .clusterName(CLUSTER)
                .name("ip-10-0-1-10.ec2.internal")
                .allocatableCpu("1930m")
                .allocatableMemory("3800Mi")
                .capacityCpu("2000m")
                .capacityMemory("4096Mi")
                .ready(true)
                .status("Ready")
                .build();

        node2 = ClusterNodeEntity.builder()
                .clusterName(CLUSTER)
                .name("ip-10-0-2-20.ec2.internal")
                .allocatableCpu("1930m")
                .allocatableMemory("3800Mi")
                .capacityCpu("2000m")
                .capacityMemory("4096Mi")
                .ready(true)
                .status("Ready")
                .build();
    }

    // ── SCENARIO 1: Perfect Fit (Low Risk) ──────────────────────────────────────
    @Test
    @DisplayName("Scenario 1: Given cluster with surplus capacity, When scale deployment moderately, Then risk is LOW")
    void scenario01_perfectFit_lowRisk() {
        // Given
        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(deploymentRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(hpaRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(pdbRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        ChangeSet changeSet = ChangeSet.builder()
                .kind(ChangeKind.SCALE)
                .target("billing-service")
                .cpuRequest("100m")
                .memRequest("256Mi")
                .cpuLimit("200m")
                .memLimit("512Mi")
                .replicas(2)
                .build();

        // When
        SimulationResult result = simulationEngine.runSimulation(SimulationRequest.builder()
                .clusterName(CLUSTER)
                .changeSet(changeSet)
                .build());

        // Then
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.getScore()).isLessThan(20);
        assertThat(result.getPredictedOutcome()).contains("safe to apply");
        verify(auditRepository, times(1)).save(any());
    }

    // ── SCENARIO 2: CPU Exhaustion (Critical Risk) ────────────────────────────────
    @Test
    @DisplayName("Scenario 2: Given 2-node cluster, When scale pods requiring 6000m CPU (> 3860m), Then risk is CRITICAL with Pending outcome")
    void scenario02_cpuExhaustion_criticalRisk() {
        // Given
        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(deploymentRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(hpaRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(pdbRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        ChangeSet changeSet = ChangeSet.builder()
                .kind(ChangeKind.SCALE)
                .target("heavy-worker")
                .cpuRequest("1000m") // 6 x 1000m = 6000m, exceeding 3860m
                .memRequest("512Mi")
                .replicas(6)
                .build();

        // When
        SimulationResult result = simulationEngine.runSimulation(SimulationRequest.builder()
                .clusterName(CLUSTER)
                .changeSet(changeSet)
                .build());

        // Then
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.getScore()).isGreaterThanOrEqualTo(45);
        assertThat(result.getFindings()).anyMatch(f -> "SchedulingFit".equals(f.getCheck()) && f.getSeverity() == FindingSeverity.CRITICAL);
        assertThat(result.getPredictedOutcome()).contains("Pending");
    }

    // ── SCENARIO 3: Memory Exhaustion (Critical Risk) ─────────────────────────────
    @Test
    @DisplayName("Scenario 3: Given cluster with 7600Mi RAM, When new deployment requests 12Gi RAM, Then risk is CRITICAL")
    void scenario03_memoryExhaustion_criticalRisk() {
        // Given
        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(deploymentRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(hpaRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(pdbRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        ChangeSet changeSet = ChangeSet.builder()
                .kind(ChangeKind.NEW_DEPLOYMENT)
                .target("in-memory-cache")
                .cpuRequest("200m")
                .memRequest("4Gi") // 3 x 4Gi = 12Gi > 7.6Gi
                .replicas(3)
                .build();

        // When
        SimulationResult result = simulationEngine.runSimulation(SimulationRequest.builder()
                .clusterName(CLUSTER)
                .changeSet(changeSet)
                .build());

        // Then
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.getFindings()).anyMatch(f -> "SchedulingFit".equals(f.getCheck()) && f.getSeverity() == FindingSeverity.CRITICAL);
    }

    // ── SCENARIO 4: LimitSanity: Limit < Request (Critical Risk) ──────────────────
    @Test
    @DisplayName("Scenario 4: Given cluster, When resource change sets cpuLimit < cpuRequest, Then risk is CRITICAL")
    void scenario04_limitLessThanRequest_criticalRisk() {
        // Given
        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(deploymentRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(hpaRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(pdbRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        ChangeSet changeSet = ChangeSet.builder()
                .kind(ChangeKind.RESOURCE_CHANGE)
                .target("auth-service")
                .cpuRequest("500m")
                .cpuLimit("200m") // invalid!
                .memRequest("1Gi")
                .memLimit("512Mi") // invalid!
                .build();

        // When
        SimulationResult result = simulationEngine.runSimulation(SimulationRequest.builder()
                .clusterName(CLUSTER)
                .changeSet(changeSet)
                .build());

        // Then
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.getFindings()).anyMatch(f -> "LimitSanity".equals(f.getCheck()) && f.getSeverity() == FindingSeverity.CRITICAL);
        assertThat(result.getPredictedOutcome()).contains("Limit cannot be less than request");
    }

    // ── SCENARIO 5: LimitSanity: Request > Largest Node (Critical Risk) ───────────
    @Test
    @DisplayName("Scenario 5: Given largest node has 1930m CPU, When request is 4000m CPU, Then risk is CRITICAL")
    void scenario05_requestExceedsLargestNode_criticalRisk() {
        // Given
        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(deploymentRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(hpaRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(pdbRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        ChangeSet changeSet = ChangeSet.builder()
                .kind(ChangeKind.NEW_DEPLOYMENT)
                .target("monolith-app")
                .cpuRequest("4000m") // > 1930m
                .memRequest("2Gi")
                .build();

        // When
        SimulationResult result = simulationEngine.runSimulation(SimulationRequest.builder()
                .clusterName(CLUSTER)
                .changeSet(changeSet)
                .build());

        // Then
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.getFindings()).anyMatch(f -> "LimitSanity".equals(f.getCheck()) &&
                f.getExplanation().contains("exceeds the allocatable capacity of the largest worker node"));
        assertThat(result.getPredictedOutcome()).contains("request exceeds largest node capacity");
    }

    // ── SCENARIO 6: LimitSanity: Missing Memory Limit (Medium Risk) ───────────────
    @Test
    @DisplayName("Scenario 6: Given cluster, When resource change omits memory limit, Then risk is MEDIUM with OOM warning")
    void scenario06_missingMemoryLimit_mediumRisk() {
        // Given
        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(deploymentRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(hpaRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(pdbRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        ChangeSet changeSet = ChangeSet.builder()
                .kind(ChangeKind.RESOURCE_CHANGE)
                .target("cron-job")
                .cpuRequest("100m")
                .memRequest("256Mi")
                // memLimit null!
                .build();

        // When
        SimulationResult result = simulationEngine.runSimulation(SimulationRequest.builder()
                .clusterName(CLUSTER)
                .changeSet(changeSet)
                .build());

        // Then
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.getFindings()).anyMatch(f -> "LimitSanity".equals(f.getCheck()) &&
                f.getExplanation().contains("Memory limit is not specified"));
    }

    // ── SCENARIO 7: NodeRemovalImpact: Violates PDB (Critical Risk) ────────────────
    @Test
    @DisplayName("Scenario 7: Given node-1 hosting auth-service with PDB having 0 disruptions allowed, When node removed, Then risk is CRITICAL")
    void scenario07_nodeRemovalViolatesPdb_criticalRisk() {
        // Given
        ClusterPodEntity authPod = ClusterPodEntity.builder()
                .clusterName(CLUSTER)
                .name("auth-service-pod-1")
                .ownerName("auth-service")
                .nodeName("ip-10-0-1-10.ec2.internal")
                .cpuRequests("150m")
                .memoryRequests("256Mi")
                .build();

        ClusterPdbEntity authPdb = ClusterPdbEntity.builder()
                .clusterName(CLUSTER)
                .name("auth-pdb")
                .minAvailable("1")
                .currentHealthy(1)
                .desiredHealthy(1)
                .disruptionsAllowed(0) // 0 disruptions allowed!
                .selector(Map.of("app", "auth-service"))
                .build();

        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(List.of(authPod));
        when(deploymentRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(hpaRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(pdbRepository.findByClusterName(CLUSTER)).thenReturn(List.of(authPdb));
        when(serviceRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        ChangeSet changeSet = ChangeSet.builder()
                .kind(ChangeKind.NODE_REMOVAL)
                .target("ip-10-0-1-10.ec2.internal")
                .build();

        // When
        SimulationResult result = simulationEngine.runSimulation(SimulationRequest.builder()
                .clusterName(CLUSTER)
                .changeSet(changeSet)
                .build());

        // Then
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.getFindings()).anyMatch(f -> "NodeRemovalImpact".equals(f.getCheck()) &&
                f.getExplanation().contains("violates PodDisruptionBudget 'auth-pdb'"));
        assertThat(result.getPredictedOutcome()).contains("violates PodDisruptionBudget");
    }

    // ── SCENARIO 8: NodeRemovalImpact: Displaced Pods Unschedulable (Critical Risk) 
    @Test
    @DisplayName("Scenario 8: Given node-1 with 3 pods requiring 1800m CPU and node-2 only having 200m surplus, When node-1 removed, Then risk is CRITICAL")
    void scenario08_nodeRemovalCapacityShortfall_criticalRisk() {
        // Given
        // Node 1 has 3 heavy pods
        ClusterPodEntity p1 = ClusterPodEntity.builder().clusterName(CLUSTER).name("p1").nodeName("ip-10-0-1-10.ec2.internal").cpuRequests("600m").memoryRequests("512Mi").build();
        ClusterPodEntity p2 = ClusterPodEntity.builder().clusterName(CLUSTER).name("p2").nodeName("ip-10-0-1-10.ec2.internal").cpuRequests("600m").memoryRequests("512Mi").build();
        ClusterPodEntity p3 = ClusterPodEntity.builder().clusterName(CLUSTER).name("p3").nodeName("ip-10-0-1-10.ec2.internal").cpuRequests("600m").memoryRequests("512Mi").build();

        // Node 2 is already loaded with 1800m of 1930m (only 130m available)
        ClusterPodEntity p4 = ClusterPodEntity.builder().clusterName(CLUSTER).name("p4").nodeName("ip-10-0-2-20.ec2.internal").cpuRequests("1800m").memoryRequests("1Gi").build();

        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(List.of(p1, p2, p3, p4));
        when(deploymentRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(hpaRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(pdbRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        ChangeSet changeSet = ChangeSet.builder()
                .kind(ChangeKind.NODE_REMOVAL)
                .target("ip-10-0-1-10.ec2.internal")
                .build();

        // When
        SimulationResult result = simulationEngine.runSimulation(SimulationRequest.builder()
                .clusterName(CLUSTER)
                .changeSet(changeSet)
                .build());

        // Then
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.getFindings()).anyMatch(f -> "NodeRemovalImpact".equals(f.getCheck()) &&
                f.getExplanation().contains("Node removal capacity shortfall"));
        assertThat(result.getPredictedOutcome()).contains("Evicted pods cannot be rescheduled");
    }

    // ── SCENARIO 9: HpaImpact: Max Scale Exceeds Capacity (High Risk) ────────────
    @Test
    @DisplayName("Scenario 9: Given HPA maxReplicas 8 requesting 800m CPU each (6400m total > 3860m cluster), When simulate, Then risk is HIGH")
    void scenario09_hpaMaxScaleExceedsCluster_highRisk() {
        // Given
        ClusterHpaEntity hpa = ClusterHpaEntity.builder()
                .clusterName(CLUSTER)
                .name("billing-hpa")
                .targetName("billing-service")
                .minReplicas(2)
                .maxReplicas(8) // 8 x 800m = 6400m > 3860m
                .build();

        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(deploymentRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(hpaRepository.findByClusterName(CLUSTER)).thenReturn(List.of(hpa));
        when(pdbRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        ChangeSet changeSet = ChangeSet.builder()
                .kind(ChangeKind.RESOURCE_CHANGE)
                .target("billing-service")
                .cpuRequest("800m")
                .cpuLimit("1000m")
                .memRequest("512Mi")
                .memLimit("1Gi")
                .build();

        // When
        SimulationResult result = simulationEngine.runSimulation(SimulationRequest.builder()
                .clusterName(CLUSTER)
                .changeSet(changeSet)
                .build());

        // Then
        assertThat(result.getRiskLevel()).isIn(RiskLevel.HIGH, RiskLevel.CRITICAL);
        assertThat(result.getFindings()).anyMatch(f -> "HpaImpact".equals(f.getCheck()) &&
                f.getExplanation().contains("HPA maxReplicas capacity shortfall"));
    }

    // ── SCENARIO 10: BlastRadius: Core Auth Service (High Risk) ──────────────────
    @Test
    @DisplayName("Scenario 10: Given auth-service with multiple dependent callers, When change proposed, Then risk is HIGH with blast radius findings")
    void scenario10_authServiceBlastRadius_highRisk() {
        // Given
        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(deploymentRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(hpaRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(pdbRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(serviceRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        ChangeSet changeSet = ChangeSet.builder()
                .kind(ChangeKind.SCALE)
                .target("auth-service")
                .cpuRequest("150m")
                .memRequest("256Mi")
                .replicas(3)
                .build();

        // When
        SimulationResult result = simulationEngine.runSimulation(SimulationRequest.builder()
                .clusterName(CLUSTER)
                .changeSet(changeSet)
                .build());

        // Then
        assertThat(result.getFindings()).anyMatch(f -> "BlastRadius".equals(f.getCheck()) &&
                f.getExplanation().contains("Broad blast radius"));
        assertThat(result.getRiskLevel()).isIn(RiskLevel.HIGH, RiskLevel.CRITICAL);
    }
}
