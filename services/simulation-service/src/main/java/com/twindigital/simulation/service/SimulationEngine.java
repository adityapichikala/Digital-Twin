package com.twindigital.simulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twindigital.simulation.check.*;
import com.twindigital.simulation.context.SimulationContext;
import com.twindigital.simulation.context.SimulationContext.BlastRadiusInfo;
import com.twindigital.simulation.entity.SimulationEntities.*;
import com.twindigital.simulation.model.SimulationModels.*;
import com.twindigital.simulation.repository.SimulationRepositories.*;
import com.twindigital.simulation.util.KubeQuantityParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationEngine {

    private final ClusterNodeRepository nodeRepository;
    private final ClusterPodRepository podRepository;
    private final ClusterDeploymentRepository deploymentRepository;
    private final ClusterHpaRepository hpaRepository;
    private final ClusterPdbRepository pdbRepository;
    private final ClusterServiceRepository serviceRepository;
    private final SimulationAuditRepository auditRepository;

    private final SchedulingFitCheck schedulingFitCheck;
    private final LimitSanityCheck limitSanityCheck;
    private final NodeRemovalImpactCheck nodeRemovalImpactCheck;
    private final HpaImpactCheck hpaImpactCheck;
    private final BlastRadiusCheck blastRadiusCheck;
    private final ServerDryRunCheck serverDryRunCheck;

    private final ObjectMapper objectMapper;

    @Value("${services.topology.url:http://localhost:8083}")
    private String topologyServiceUrl;

    @Value("${services.cost-risk.url:http://localhost:8085}")
    private String costRiskServiceUrl;

    private static final String DEFAULT_CLUSTER = "twindigital-cluster";

    @Transactional
    public SimulationResult runSimulation(SimulationRequest request) {
        String clusterName = (request.getClusterName() != null && !request.getClusterName().isBlank())
                ? request.getClusterName() : DEFAULT_CLUSTER;
        String user = (request.getUser() != null && !request.getUser().isBlank())
                ? request.getUser() : "anonymous";
        ChangeSet cs = request.getChangeSet();
        if (cs == null) {
            throw new IllegalArgumentException("changeSet is required");
        }

        // 1. Build Simulation Context from DB (or fallback seed)
        SimulationContext context = buildContext(clusterName, user, cs);

        // 2. Execute all separate unit-tested check classes
        List<SimulationFinding> findings = new ArrayList<>();
        findings.addAll(schedulingFitCheck.evaluate(context));
        findings.addAll(limitSanityCheck.evaluate(context));
        findings.addAll(nodeRemovalImpactCheck.evaluate(context));
        findings.addAll(hpaImpactCheck.evaluate(context));
        findings.addAll(blastRadiusCheck.evaluate(context));
        findings.addAll(serverDryRunCheck.evaluate(context));

        // 3. Compute Risk Score & Risk Level
        int score = calculateRiskScore(findings);
        RiskLevel riskLevel = determineRiskLevel(score, findings);

        // 4. Synthesize Plain English Predicted Outcome
        String predictedOutcome = synthesizePredictedOutcome(cs, findings, riskLevel);

        // 5. Compute Cluster Capacity Summary
        ClusterCapacitySummary capacitySummary = computeCapacitySummary(context);

        // 5.5 Call cost-risk estimate-change (with graceful fallback)
        Double costDelta = estimateCostDelta(cs);

        // 6. Assemble Result
        UUID simulationId = UUID.randomUUID();
        SimulationResult result = SimulationResult.builder()
                .simulationId(simulationId.toString())
                .riskLevel(riskLevel)
                .score(score)
                .findings(findings)
                .predictedOutcome(predictedOutcome)
                .capacitySummary(capacitySummary)
                .costDelta(costDelta)
                .executedAt(Instant.now())
                .build();

        // 7. Write to simulation_audit table
        recordAudit(simulationId, user, clusterName, cs, result, riskLevel, score, predictedOutcome, findings.size());


        return result;
    }

    public SimulationContext buildContext(String clusterName, String user, ChangeSet cs) {
        List<ClusterNodeEntity> dbNodes = nodeRepository.findByClusterName(clusterName);
        List<ClusterPodEntity> dbPods = podRepository.findByClusterName(clusterName);
        List<ClusterDeploymentEntity> dbDeployments = deploymentRepository.findByClusterName(clusterName);
        List<ClusterHpaEntity> dbHpas = hpaRepository.findByClusterName(clusterName);
        List<ClusterPdbEntity> dbPdbs = pdbRepository.findByClusterName(clusterName);
        List<ClusterServiceEntity> dbServices = serviceRepository.findByClusterName(clusterName);

        if (dbNodes.isEmpty() && dbPods.isEmpty() && dbDeployments.isEmpty()) {
            log.info("DB snapshot empty for cluster '{}'; using realistic seed snapshot", clusterName);
            dbNodes = createDefaultSeedNodes(clusterName);
            dbPods = createDefaultSeedPods(clusterName);
            dbDeployments = createDefaultSeedDeployments(clusterName);
            dbHpas = createDefaultSeedHpas(clusterName);
            dbPdbs = createDefaultSeedPdbs(clusterName);
            dbServices = createDefaultSeedServices(clusterName);
        }

        return SimulationContext.builder()
                .clusterName(clusterName)
                .user(user)
                .changeSet(cs)
                .nodes(dbNodes)
                .pods(dbPods)
                .deployments(dbDeployments)
                .hpas(dbHpas)
                .pdbs(dbPdbs)
                .services(dbServices)
                .blastRadiusResolver(this::fetchTopologyBlastRadius)
                .build();
    }

    private BlastRadiusInfo fetchTopologyBlastRadius(String target) {
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(topologyServiceUrl)
                    .build();

            Map<?, ?> resp = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/topology/blast-radius")
                            .queryParam("target", target)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp != null) {
                List<?> upstreamRaw = (List<?>) resp.get("upstream");
                List<String> upstream = new ArrayList<>();
                if (upstreamRaw != null) {
                    for (Object item : upstreamRaw) {
                        if (item instanceof Map<?, ?> m && m.get("name") != null) {
                            upstream.add(m.get("name").toString());
                        }
                    }
                }

                List<?> downstreamRaw = (List<?>) resp.get("downstream");
                List<String> downstream = new ArrayList<>();
                if (downstreamRaw != null) {
                    for (Object item : downstreamRaw) {
                        if (item instanceof Map<?, ?> m && m.get("name") != null) {
                            downstream.add(m.get("name").toString());
                        }
                    }
                }

                int total = resp.get("totalImpacted") instanceof Number n ? n.intValue() : upstream.size() + downstream.size();
                String risk = resp.get("riskScore") != null ? resp.get("riskScore").toString() : "MEDIUM";

                return BlastRadiusInfo.builder()
                        .target(target)
                        .upstreamCallers(upstream)
                        .downstreamDependencies(downstream)
                        .totalImpacted(total)
                        .riskScore(risk)
                        .build();
            }
        } catch (Exception e) {
            log.debug("HTTP call to topology-service unavailable ({}); using internal heuristic", e.getMessage());
        }
        return null;
    }

    private Double estimateCostDelta(ChangeSet cs) {
        if (cs == null || costRiskServiceUrl == null || costRiskServiceUrl.isBlank()) {
            return null;
        }
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(costRiskServiceUrl)
                    .build();

            Map<String, Object> reqBody = new HashMap<>();
            if (cs.getKind() != null) {
                reqBody.put("kind", cs.getKind().name());
            }
            reqBody.put("target", cs.getTarget() != null ? cs.getTarget() : "workload");
            reqBody.put("namespace", cs.getNamespace() != null ? cs.getNamespace() : "twindigital");
            if (cs.getReplicas() != null) {
                reqBody.put("replicas", cs.getReplicas());
            }
            if (cs.getCpuRequest() != null) {
                reqBody.put("cpuRequest", cs.getCpuRequest());
            }
            if (cs.getMemRequest() != null) {
                reqBody.put("memRequest", cs.getMemRequest());
            }

            Map<?, ?> resp = webClient.post()
                    .uri("/api/v1/cost/estimate-change")
                    .bodyValue(reqBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(java.time.Duration.ofSeconds(3));

            if (resp != null) {
                Object delta = resp.get("deltaMonthlyUsd");
                if (delta == null) {
                    delta = resp.get("costDeltaUsd");
                }
                if (delta instanceof Number n) {
                    return n.doubleValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to call cost-risk /api/v1/cost/estimate-change at {} (graceful fallback): {}",
                    costRiskServiceUrl, e.getMessage());
        }
        return null;
    }

    private int calculateRiskScore(List<SimulationFinding> findings) {
        int score = 0;
        for (SimulationFinding f : findings) {
            switch (f.getSeverity()) {
                case CRITICAL -> score += 45;
                case HIGH -> score += 25;
                case MEDIUM -> score += 15;
                case LOW -> score += 5;
                case INFO -> score += 0;
            }
        }
        return Math.min(100, score);
    }

    private RiskLevel determineRiskLevel(int score, List<SimulationFinding> findings) {
        boolean hasCritical = findings.stream().anyMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL);
        boolean hasHigh = findings.stream().anyMatch(f -> f.getSeverity() == FindingSeverity.HIGH);
        boolean hasMedium = findings.stream().anyMatch(f -> f.getSeverity() == FindingSeverity.MEDIUM);

        if (score >= 70 || hasCritical) {
            return RiskLevel.CRITICAL;
        } else if (score >= 40 || hasHigh) {
            return RiskLevel.HIGH;
        } else if (score >= 20 || hasMedium) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private String synthesizePredictedOutcome(ChangeSet cs, List<SimulationFinding> findings, RiskLevel riskLevel) {
        // 1. Check LimitSanity first (single-pod node limits & validation rules)
        for (SimulationFinding f : findings) {
            if ("LimitSanity".equals(f.getCheck()) && f.getSeverity() == FindingSeverity.CRITICAL) {
                if (f.getExplanation().contains("exceeds the allocatable capacity")) {
                    return "Pod cannot be scheduled on any node: request exceeds largest node capacity";
                }
                if (f.getExplanation().contains("less than")) {
                    return "Validation failure: Limit cannot be less than request";
                }
            }
        }

        // 2. Check NodeRemovalImpact (PDB violations and node drains)
        for (SimulationFinding f : findings) {
            if ("NodeRemovalImpact".equals(f.getCheck()) && f.getSeverity() == FindingSeverity.CRITICAL) {
                if (f.getExplanation().contains("violates PodDisruptionBudget")) {
                    return "Node removal violates PodDisruptionBudget: draining will be blocked";
                }
                if (f.getExplanation().contains("would stay 'Pending'")) {
                    return "Evicted pods cannot be rescheduled on remaining nodes";
                }
            }
        }

        // 3. Check SchedulingFit (aggregate cluster capacity & bin packing)
        for (SimulationFinding f : findings) {
            if ("SchedulingFit".equals(f.getCheck()) && f.getSeverity() == FindingSeverity.CRITICAL) {
                if (f.getExplanation().contains("would stay 'Pending'")) {
                    String part = f.getExplanation().substring(f.getExplanation().indexOf(";") + 1).trim();
                    if (part.contains(".")) part = part.substring(0, part.indexOf(".")).trim();
                    return part;
                }
                return "Pods would stay Pending due to insufficient cluster capacity";
            }
        }

        if (riskLevel == RiskLevel.LOW) {
            return "Change is safe to apply: all pods schedulable with sufficient cluster capacity remaining.";
        } else if (riskLevel == RiskLevel.MEDIUM) {
            return "Change is schedulable with elevated operational caution.";
        } else if (riskLevel == RiskLevel.HIGH) {
            return "Change poses significant risk to cluster capacity or dependent services.";
        }
        return "Change will cause pod scheduling failure or cluster instability.";
    }

    private ClusterCapacitySummary computeCapacitySummary(SimulationContext context) {
        int totalNodes = context.getNodes().size();
        int readyNodes = (int) context.getNodes().stream().filter(ClusterNodeEntity::isReady).count();

        long allocCpu = context.getTotalClusterAllocatableCpu();
        long allocMem = context.getTotalClusterAllocatableMem();

        long reqCpu = 0L;
        long reqMem = 0L;
        for (ClusterPodEntity p : context.getPods()) {
            reqCpu += KubeQuantityParser.parseCpuMillis(p.getCpuRequests());
            reqMem += KubeQuantityParser.parseMemoryBytes(p.getMemoryRequests());
        }

        return ClusterCapacitySummary.builder()
                .totalNodes(totalNodes)
                .readyNodes(readyNodes)
                .totalCpuAllocatableMillis(allocCpu)
                .totalCpuRequestedMillis(reqCpu)
                .availableCpuMillis(Math.max(0L, allocCpu - reqCpu))
                .totalMemAllocatableBytes(allocMem)
                .totalMemRequestedBytes(reqMem)
                .availableMemBytes(Math.max(0L, allocMem - reqMem))
                .totalPods(context.getPods().size())
                .build();
    }

    private void recordAudit(UUID simulationId, String user, String cluster, ChangeSet cs,
                             SimulationResult result, RiskLevel riskLevel, int score,
                             String outcome, int findingsCount) {
        try {
            String csJson = objectMapper.writeValueAsString(cs);
            String resJson = objectMapper.writeValueAsString(result);

            SimulationAuditEntity audit = SimulationAuditEntity.builder()
                    .simulationId(simulationId)
                    .userId(user)
                    .clusterName(cluster)
                    .changeSet(csJson)
                    .result(resJson)
                    .riskLevel(riskLevel.name())
                    .score(score)
                    .predictedOutcome(outcome)
                    .findingsCount(findingsCount)
                    .executedAt(Instant.now())
                    .build();

            auditRepository.save(audit);
            log.info("Saved simulation audit log id={} riskLevel={} score={}", simulationId, riskLevel, score);
        } catch (Exception e) {
            log.error("Failed to write simulation audit record: {}", e.getMessage(), e);
        }
    }

    // Default Seed Snapshots for realistic cluster simulation
    private List<ClusterNodeEntity> createDefaultSeedNodes(String cluster) {
        return List.of(
                ClusterNodeEntity.builder()
                        .clusterName(cluster)
                        .name("ip-10-0-1-10.ec2.internal")
                        .allocatableCpu("1930m")
                        .allocatableMemory("3800Mi")
                        .capacityCpu("2000m")
                        .capacityMemory("4096Mi")
                        .ready(true)
                        .status("Ready")
                        .labels(Map.of("kubernetes.io/hostname", "ip-10-0-1-10.ec2.internal", "node.kubernetes.io/instance-type", "t3.medium"))
                        .build(),
                ClusterNodeEntity.builder()
                        .clusterName(cluster)
                        .name("ip-10-0-2-20.ec2.internal")
                        .allocatableCpu("1930m")
                        .allocatableMemory("3800Mi")
                        .capacityCpu("2000m")
                        .capacityMemory("4096Mi")
                        .ready(true)
                        .status("Ready")
                        .labels(Map.of("kubernetes.io/hostname", "ip-10-0-2-20.ec2.internal", "node.kubernetes.io/instance-type", "t3.medium"))
                        .build()
        );
    }

    private List<ClusterDeploymentEntity> createDefaultSeedDeployments(String cluster) {
        return List.of(
                ClusterDeploymentEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("auth-service")
                        .replicas(2)
                        .readyReplicas(2)
                        .availableReplicas(2)
                        .labels(Map.of("app", "auth-service"))
                        .build(),
                ClusterDeploymentEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("billing-service")
                        .replicas(2)
                        .readyReplicas(2)
                        .availableReplicas(2)
                        .labels(Map.of("app", "billing-service"))
                        .build()
        );
    }

    private List<ClusterPodEntity> createDefaultSeedPods(String cluster) {
        return List.of(
                ClusterPodEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("auth-service-7bc89d-abc12")
                        .nodeName("ip-10-0-1-10.ec2.internal")
                        .ownerKind("Deployment")
                        .ownerName("auth-service")
                        .cpuRequests("150m")
                        .memoryRequests("256Mi")
                        .cpuLimits("500m")
                        .memoryLimits("512Mi")
                        .phase("Running")
                        .restarts(0)
                        .labels(Map.of("app", "auth-service"))
                        .build(),
                ClusterPodEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("auth-service-7bc89d-xyz89")
                        .nodeName("ip-10-0-2-20.ec2.internal")
                        .ownerKind("Deployment")
                        .ownerName("auth-service")
                        .cpuRequests("150m")
                        .memoryRequests("256Mi")
                        .cpuLimits("500m")
                        .memoryLimits("512Mi")
                        .phase("Running")
                        .restarts(0)
                        .labels(Map.of("app", "auth-service"))
                        .build(),
                ClusterPodEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("billing-service-59bc6d-pl92x")
                        .nodeName("ip-10-0-1-10.ec2.internal")
                        .ownerKind("Deployment")
                        .ownerName("billing-service")
                        .cpuRequests("200m")
                        .memoryRequests("512Mi")
                        .cpuLimits("600m")
                        .memoryLimits("1Gi")
                        .phase("Running")
                        .restarts(0)
                        .labels(Map.of("app", "billing-service"))
                        .build()
        );
    }

    private List<ClusterHpaEntity> createDefaultSeedHpas(String cluster) {
        return List.of(
                ClusterHpaEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("billing-hpa")
                        .targetKind("Deployment")
                        .targetName("billing-service")
                        .minReplicas(2)
                        .maxReplicas(6)
                        .currentReplicas(2)
                        .desiredReplicas(2)
                        .build()
        );
    }

    private List<ClusterPdbEntity> createDefaultSeedPdbs(String cluster) {
        return List.of(
                ClusterPdbEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("auth-pdb")
                        .minAvailable("1")
                        .currentHealthy(2)
                        .desiredHealthy(2)
                        .disruptionsAllowed(1)
                        .selector(Map.of("app", "auth-service"))
                        .build()
        );
    }

    private List<ClusterServiceEntity> createDefaultSeedServices(String cluster) {
        return List.of(
                ClusterServiceEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("auth-service")
                        .serviceType("ClusterIP")
                        .clusterIp("172.20.10.2")
                        .build(),
                ClusterServiceEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("billing-service")
                        .serviceType("ClusterIP")
                        .clusterIp("172.20.10.3")
                        .build()
        );
    }
}
