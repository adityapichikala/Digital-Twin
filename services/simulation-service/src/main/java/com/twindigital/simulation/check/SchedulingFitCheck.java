package com.twindigital.simulation.check;

import com.twindigital.simulation.context.SimulationContext;
import com.twindigital.simulation.entity.SimulationEntities.ClusterDeploymentEntity;
import com.twindigital.simulation.entity.SimulationEntities.ClusterNodeEntity;
import com.twindigital.simulation.entity.SimulationEntities.ClusterPodEntity;
import com.twindigital.simulation.model.SimulationModels.ChangeKind;
import com.twindigital.simulation.model.SimulationModels.ChangeSet;
import com.twindigital.simulation.model.SimulationModels.FindingSeverity;
import com.twindigital.simulation.model.SimulationModels.SimulationFinding;
import com.twindigital.simulation.util.KubeQuantityParser;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SchedulingFitCheck implements SimulationCheck {

    @Override
    public String getName() {
        return "SchedulingFit";
    }

    private static class NodeBin {
        final String name;
        final Map<String, String> labels;
        long cpuAvail;
        long memAvail;

        NodeBin(String name, Map<String, String> labels, long cpuAvail, long memAvail) {
            this.name = name;
            this.labels = labels != null ? labels : Collections.emptyMap();
            this.cpuAvail = cpuAvail;
            this.memAvail = memAvail;
        }

        boolean matchesSelectors(Map<String, String> selectors) {
            if (selectors == null || selectors.isEmpty()) return true;
            for (Map.Entry<String, String> entry : selectors.entrySet()) {
                if (!entry.getValue().equals(this.labels.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public List<SimulationFinding> evaluate(SimulationContext context) {
        List<SimulationFinding> findings = new ArrayList<>();
        ChangeSet cs = context.getChangeSet();
        if (cs == null) return findings;

        // Skip pure NODE_REMOVAL here (handled in NodeRemovalImpactCheck)
        if (cs.getKind() == ChangeKind.NODE_REMOVAL) {
            return findings;
        }

        // 1. Gather baseline pod requirements
        long reqCpu = KubeQuantityParser.parseCpuMillis(cs.getCpuRequest());
        long reqMem = KubeQuantityParser.parseMemoryBytes(cs.getMemRequest());

        int totalPodsToPlace = 0;
        String targetName = cs.getTarget() != null ? cs.getTarget() : "workload";

        Optional<ClusterDeploymentEntity> optDep = context.findDeployment(targetName);
        List<ClusterPodEntity> targetPods = context.getPodsForDeployment(targetName);

        if (cs.getKind() == ChangeKind.SCALE) {
            int currentReplicas = optDep.map(ClusterDeploymentEntity::getReplicas)
                    .orElse(Math.max(1, targetPods.size()));
            int targetReplicas = cs.getReplicas() != null ? cs.getReplicas() : currentReplicas;

            if (targetReplicas > currentReplicas) {
                totalPodsToPlace = targetReplicas - currentReplicas;
                // If requests weren't specified in scale change, adopt existing pod requests
                if (reqCpu == 0L && !targetPods.isEmpty()) {
                    reqCpu = KubeQuantityParser.parseCpuMillis(targetPods.get(0).getCpuRequests());
                }
                if (reqMem == 0L && !targetPods.isEmpty()) {
                    reqMem = KubeQuantityParser.parseMemoryBytes(targetPods.get(0).getMemoryRequests());
                }
            } else {
                // Scale down - capacity freed, no new pods to schedule
                return findings;
            }
        } else if (cs.getKind() == ChangeKind.RESOURCE_CHANGE) {
            int replicas = cs.getReplicas() != null ? cs.getReplicas() :
                    optDep.map(ClusterDeploymentEntity::getReplicas).orElse(Math.max(1, targetPods.size()));
            totalPodsToPlace = replicas;
        } else if (cs.getKind() == ChangeKind.NEW_DEPLOYMENT) {
            totalPodsToPlace = cs.getReplicas() != null ? Math.max(1, cs.getReplicas()) : 1;
        }

        if (totalPodsToPlace <= 0) {
            return findings;
        }

        // Fallback default modest requests if unconfigured
        if (reqCpu == 0L) reqCpu = 100L; // 100m default
        if (reqMem == 0L) reqMem = 128L * 1024L * 1024L; // 128Mi default

        // 2. Initialize Node bins with available capacity (allocatable - existing requests)
        Map<String, NodeBin> nodeBins = new HashMap<>();
        for (ClusterNodeEntity node : context.getNodes()) {
            if (!node.isReady()) continue;

            long allocCpu = KubeQuantityParser.parseCpuMillis(node.getAllocatableCpu());
            long allocMem = KubeQuantityParser.parseMemoryBytes(node.getAllocatableMemory());

            long usedCpu = 0L;
            long usedMem = 0L;

            for (ClusterPodEntity pod : context.getPodsOnNode(node.getName())) {
                // If RESOURCE_CHANGE, exclude existing pods of target from usage
                if (cs.getKind() == ChangeKind.RESOURCE_CHANGE && targetPods.contains(pod)) {
                    continue;
                }
                usedCpu += KubeQuantityParser.parseCpuMillis(pod.getCpuRequests());
                usedMem += KubeQuantityParser.parseMemoryBytes(pod.getMemoryRequests());
            }

            long availCpu = Math.max(0L, allocCpu - usedCpu);
            long availMem = Math.max(0L, allocMem - usedMem);

            nodeBins.put(node.getName(), new NodeBin(node.getName(), node.getLabels(), availCpu, availMem));
        }

        if (nodeBins.isEmpty()) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.CRITICAL)
                    .explanation("Cluster has 0 Ready nodes. No pods can be scheduled; all " + totalPodsToPlace + " pod(s) will remain in 'Pending' state.")
                    .suggestedFix("Check cluster node health and ensure at least one worker node is in 'Ready' status.")
                    .build());
            return findings;
        }

        // Filter by nodeSelectors if present
        List<NodeBin> candidateBins = nodeBins.values().stream()
                .filter(b -> b.matchesSelectors(cs.getNodeSelectors()))
                .toList();

        if (candidateBins.isEmpty()) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.CRITICAL)
                    .explanation(String.format("No Ready nodes match the required nodeSelector %s. All %d pod(s) will stay Pending.",
                            cs.getNodeSelectors(), totalPodsToPlace))
                    .suggestedFix("Add labels matching the nodeSelector to worker nodes or adjust deployment nodeSelector.")
                    .build());
            return findings;
        }

        // 3. Bin Packing Placement (Best-Fit decreasing / smallest remaining capacity)
        int placedCount = 0;
        int unscheduledCount = 0;

        for (int i = 0; i < totalPodsToPlace; i++) {
            NodeBin bestFit = null;
            long minRemainingCpu = Long.MAX_VALUE;

            for (NodeBin bin : candidateBins) {
                if (bin.cpuAvail >= reqCpu && bin.memAvail >= reqMem) {
                    long remainingAfter = bin.cpuAvail - reqCpu;
                    if (remainingAfter < minRemainingCpu) {
                        minRemainingCpu = remainingAfter;
                        bestFit = bin;
                    }
                }
            }

            if (bestFit != null) {
                bestFit.cpuAvail -= reqCpu;
                bestFit.memAvail -= reqMem;
                placedCount++;
            } else {
                unscheduledCount++;
            }
        }

        // 4. Generate findings
        if (unscheduledCount > 0) {
            FindingSeverity severity = (placedCount == 0) ? FindingSeverity.CRITICAL : FindingSeverity.HIGH;
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(severity)
                    .explanation(String.format("Bin-packing check failed: Only %d of %d pod(s) can be scheduled; %d pod(s) would stay 'Pending' due to insufficient allocatable CPU/Memory on candidate nodes (requested %s CPU, %s RAM).",
                            placedCount, totalPodsToPlace, unscheduledCount,
                            KubeQuantityParser.formatCpu(reqCpu), KubeQuantityParser.formatMemory(reqMem)))
                    .suggestedFix(String.format("Scale the node group with additional worker nodes or reduce pod resource request from %s CPU to fit within available capacity.",
                            KubeQuantityParser.formatCpu(reqCpu)))
                    .build());
        }

        return findings;
    }
}
