package com.twindigital.simulation.check;

import com.twindigital.simulation.context.SimulationContext;
import com.twindigital.simulation.context.SimulationContext.BlastRadiusInfo;
import com.twindigital.simulation.entity.SimulationEntities.*;
import com.twindigital.simulation.model.SimulationModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationChecksUnitTest {

    private SchedulingFitCheck schedulingFitCheck;
    private LimitSanityCheck limitSanityCheck;
    private NodeRemovalImpactCheck nodeRemovalImpactCheck;
    private HpaImpactCheck hpaImpactCheck;
    private BlastRadiusCheck blastRadiusCheck;
    private ServerDryRunCheck serverDryRunCheck;

    @BeforeEach
    void setUp() {
        schedulingFitCheck = new SchedulingFitCheck();
        limitSanityCheck = new LimitSanityCheck();
        nodeRemovalImpactCheck = new NodeRemovalImpactCheck();
        hpaImpactCheck = new HpaImpactCheck();
        blastRadiusCheck = new BlastRadiusCheck();
        serverDryRunCheck = new ServerDryRunCheck();
    }

    @Test
    @DisplayName("SchedulingFit: bin-packing should detect when pods cannot fit due to CPU exhaustion")
    void testSchedulingFitCpuExhaustion() {
        ClusterNodeEntity node1 = ClusterNodeEntity.builder()
                .name("node-1")
                .allocatableCpu("1000m")
                .allocatableMemory("2Gi")
                .ready(true)
                .build();

        // 1 existing pod using 800m CPU
        ClusterPodEntity pod1 = ClusterPodEntity.builder()
                .name("p1")
                .nodeName("node-1")
                .cpuRequests("800m")
                .memoryRequests("500Mi")
                .build();

        // Proposed new deployment needing 500m CPU
        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.NEW_DEPLOYMENT)
                .target("new-app")
                .cpuRequest("500m")
                .memRequest("500Mi")
                .replicas(1)
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(node1))
                .pods(List.of(pod1))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = schedulingFitCheck.evaluate(ctx);

        assertThat(findings).isNotEmpty();
        assertThat(findings.get(0).getSeverity()).isEqualTo(FindingSeverity.CRITICAL);
        assertThat(findings.get(0).getExplanation()).contains("Bin-packing check failed");
        assertThat(findings.get(0).getExplanation()).contains("1 pod(s) would stay 'Pending'");
    }

    @Test
    @DisplayName("LimitSanity: should flag critical when limit < request")
    void testLimitSanityLimitLessThanRequest() {
        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.RESOURCE_CHANGE)
                .target("auth-service")
                .cpuRequest("500m")
                .cpuLimit("200m") // invalid!
                .memRequest("1Gi")
                .memLimit("512Mi") // invalid!
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = limitSanityCheck.evaluate(ctx);

        assertThat(findings).hasSize(2);
        assertThat(findings).allMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL);
        assertThat(findings.get(0).getExplanation()).contains("CPU limit");
        assertThat(findings.get(1).getExplanation()).contains("Memory limit");
    }

    @Test
    @DisplayName("LimitSanity: should flag when request > largest node allocatable")
    void testLimitSanityRequestExceedsLargestNode() {
        ClusterNodeEntity node1 = ClusterNodeEntity.builder()
                .name("node-1")
                .allocatableCpu("2000m")
                .allocatableMemory("4Gi")
                .ready(true)
                .build();

        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.NEW_DEPLOYMENT)
                .target("heavy-ml")
                .cpuRequest("4000m") // > 2000m
                .memRequest("8Gi")   // > 4Gi
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(node1))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = limitSanityCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL &&
                f.getExplanation().contains("exceeds the allocatable capacity of the largest worker node"));
    }

    @Test
    @DisplayName("NodeRemovalImpact: should flag critical when PDB has 0 disruptions allowed")
    void testNodeRemovalViolatesPdb() {
        ClusterNodeEntity node1 = ClusterNodeEntity.builder()
                .name("node-1")
                .allocatableCpu("2000m")
                .allocatableMemory("4Gi")
                .ready(true)
                .build();

        ClusterPodEntity authPod = ClusterPodEntity.builder()
                .name("auth-service-pod-1")
                .ownerName("auth-service")
                .nodeName("node-1")
                .cpuRequests("200m")
                .memoryRequests("256Mi")
                .build();

        ClusterPdbEntity authPdb = ClusterPdbEntity.builder()
                .name("auth-pdb")
                .minAvailable("1")
                .currentHealthy(1)
                .desiredHealthy(1)
                .disruptionsAllowed(0) // 0 disruptions allowed!
                .selector(Map.of("app", "auth-service"))
                .build();

        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.NODE_REMOVAL)
                .target("node-1")
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(node1))
                .pods(List.of(authPod))
                .pdbs(List.of(authPdb))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = nodeRemovalImpactCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL &&
                f.getExplanation().contains("violates PodDisruptionBudget 'auth-pdb'"));
    }

    @Test
    @DisplayName("HpaImpact: should flag high risk when maxReplicas capacity exceeds cluster allocatable")
    void testHpaMaxReplicasExceedsCapacity() {
        ClusterNodeEntity node = ClusterNodeEntity.builder()
                .name("node-1")
                .allocatableCpu("2000m")
                .allocatableMemory("4Gi")
                .ready(true)
                .build();

        ClusterHpaEntity hpa = ClusterHpaEntity.builder()
                .name("billing-hpa")
                .targetName("billing-service")
                .minReplicas(2)
                .maxReplicas(8) // at 8x 500m = 4000m CPU needed, but cluster only has 2000m
                .build();

        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.RESOURCE_CHANGE)
                .target("billing-service")
                .cpuRequest("500m")
                .memRequest("512Mi")
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(node))
                .hpas(List.of(hpa))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = hpaImpactCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.HIGH &&
                f.getExplanation().contains("HPA maxReplicas capacity shortfall"));
    }

    @Test
    @DisplayName("BlastRadius: should flag broad blast radius for core auth service")
    void testBlastRadiusForAuthService() {
        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.RESOURCE_CHANGE)
                .target("auth-service")
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .changeSet(cs)
                .blastRadiusResolver(target -> BlastRadiusInfo.builder()
                        .target(target)
                        .upstreamCallers(List.of("gateway", "billing-service", "simulation-service", "topology-service"))
                        .downstreamDependencies(List.of("postgres", "redis"))
                        .totalImpacted(6)
                        .riskScore("CRITICAL")
                        .build())
                .build();

        List<SimulationFinding> findings = blastRadiusCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.HIGH &&
                f.getExplanation().contains("Broad blast radius: Target service 'auth-service' has 4 upstream caller(s)"));
    }

    @Test
    @DisplayName("ServerDryRun: should flag RFC 1123 naming violations")
    void testServerDryRunInvalidName() {
        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.NEW_DEPLOYMENT)
                .target("INVALID_NAME_WITH_CAPS_AND_UNDERSCORES")
                .dryRunKubeApi(true)
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = serverDryRunCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL &&
                f.getExplanation().contains("invalid Kubernetes name"));
    }
}
