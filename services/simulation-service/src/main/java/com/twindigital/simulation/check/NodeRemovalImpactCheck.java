package com.twindigital.simulation.check;

import com.twindigital.simulation.context.SimulationContext;
import com.twindigital.simulation.entity.SimulationEntities.ClusterNodeEntity;
import com.twindigital.simulation.entity.SimulationEntities.ClusterPdbEntity;
import com.twindigital.simulation.entity.SimulationEntities.ClusterPodEntity;
import com.twindigital.simulation.model.SimulationModels.ChangeKind;
import com.twindigital.simulation.model.SimulationModels.ChangeSet;
import com.twindigital.simulation.model.SimulationModels.FindingSeverity;
import com.twindigital.simulation.model.SimulationModels.SimulationFinding;
import com.twindigital.simulation.util.KubeQuantityParser;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class NodeRemovalImpactCheck implements SimulationCheck {

    @Override
    public String getName() {
        return "NodeRemovalImpact";
    }

    private static class RemainingNodeBin {
        final String name;
        final Map<String, String> labels;
        long cpuAvail;
        long memAvail;

        RemainingNodeBin(String name, Map<String, String> labels, long cpuAvail, long memAvail) {
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
        if (cs == null || cs.getKind() != ChangeKind.NODE_REMOVAL) {
            return findings;
        }

        String targetNodeName = cs.getTarget();
        if (targetNodeName == null || targetNodeName.isBlank()) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.HIGH)
                    .explanation("Node removal proposed without specifying target node name.")
                    .suggestedFix("Specify the exact node name to be cordoned/drained in the change set.")
                    .build());
            return findings;
        }

        Optional<ClusterNodeEntity> targetNodeOpt = context.findNode(targetNodeName);
        if (targetNodeOpt.isEmpty()) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.HIGH)
                    .explanation(String.format("Target node '%s' does not exist in cluster state.", targetNodeName))
                    .suggestedFix("Ensure target node name matches an active node in the cluster snapshot.")
                    .build());
            return findings;
        }

        // 1. Collect all non-DaemonSet pods running on this node
        List<ClusterPodEntity> displacedPods = context.getPodsOnNode(targetNodeName).stream()
                .filter(p -> !"DaemonSet".equalsIgnoreCase(p.getOwnerKind()))
                .toList();

        // 2. PodDisruptionBudget (PDB) Validation
        Set<String> checkedPdbs = new HashSet<>();
        for (ClusterPodEntity pod : displacedPods) {
            String appKey = pod.getOwnerName() != null ? pod.getOwnerName() :
                    (pod.getLabels() != null ? pod.getLabels().get("app") : null);

            if (appKey == null) continue;

            Optional<ClusterPdbEntity> pdbOpt = context.findPdbForTarget(appKey);
            if (pdbOpt.isPresent()) {
                ClusterPdbEntity pdb = pdbOpt.get();
                if (checkedPdbs.add(pdb.getName())) {
                    Integer disruptionsAllowed = pdb.getDisruptionsAllowed();
                    if (disruptionsAllowed != null && disruptionsAllowed <= 0) {
                        findings.add(SimulationFinding.builder()
                                .check(getName())
                                .severity(FindingSeverity.CRITICAL)
                                .explanation(String.format("Node removal violates PodDisruptionBudget '%s': 0 disruptions currently allowed (minAvailable: %s, current healthy: %d, desired healthy: %d). Evicting pod '%s' will violate the disruption budget.",
                                        pdb.getName(), pdb.getMinAvailable(), pdb.getCurrentHealthy() != null ? pdb.getCurrentHealthy() : 0,
                                        pdb.getDesiredHealthy() != null ? pdb.getDesiredHealthy() : 0, pod.getName()))
                                .suggestedFix(String.format("Scale up deployment '%s' replicas before draining node '%s' or adjust PDB '%s'.",
                                        appKey, targetNodeName, pdb.getName()))
                                .build());
                    }
                }
            }
        }

        // 3. Remaining Nodes Reschedulability Analysis
        List<RemainingNodeBin> remainingBins = new ArrayList<>();
        for (ClusterNodeEntity node : context.getNodes()) {
            if (!node.isReady() || node.getName().equalsIgnoreCase(targetNodeName)) {
                continue; // exclude unready nodes and target node
            }

            long allocCpu = KubeQuantityParser.parseCpuMillis(node.getAllocatableCpu());
            long allocMem = KubeQuantityParser.parseMemoryBytes(node.getAllocatableMemory());

            long usedCpu = 0L;
            long usedMem = 0L;
            for (ClusterPodEntity p : context.getPodsOnNode(node.getName())) {
                usedCpu += KubeQuantityParser.parseCpuMillis(p.getCpuRequests());
                usedMem += KubeQuantityParser.parseMemoryBytes(p.getMemoryRequests());
            }

            remainingBins.add(new RemainingNodeBin(node.getName(), node.getLabels(),
                    Math.max(0L, allocCpu - usedCpu), Math.max(0L, allocMem - usedMem)));
        }

        if (remainingBins.isEmpty() && !displacedPods.isEmpty()) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.CRITICAL)
                    .explanation(String.format("Removing node '%s' leaves 0 alternative worker nodes. All %d displaced pod(s) will become unschedulable.",
                            targetNodeName, displacedPods.size()))
                    .suggestedFix(String.format("Provision at least 1 additional worker node before draining '%s'.", targetNodeName))
                    .build());
            return findings;
        }

        int rescheduledCount = 0;
        int unschedulableCount = 0;
        long totalDisplacedCpu = 0L;
        long totalDisplacedMem = 0L;

        for (ClusterPodEntity pod : displacedPods) {
            long reqCpu = KubeQuantityParser.parseCpuMillis(pod.getCpuRequests());
            long reqMem = KubeQuantityParser.parseMemoryBytes(pod.getMemoryRequests());
            totalDisplacedCpu += reqCpu;
            totalDisplacedMem += reqMem;

            RemainingNodeBin bestFit = null;
            long minRemaining = Long.MAX_VALUE;

            for (RemainingNodeBin bin : remainingBins) {
                if (bin.matchesSelectors(pod.getLabels()) && bin.cpuAvail >= reqCpu && bin.memAvail >= reqMem) {
                    long remainingAfter = bin.cpuAvail - reqCpu;
                    if (remainingAfter < minRemaining) {
                        minRemaining = remainingAfter;
                        bestFit = bin;
                    }
                }
            }

            if (bestFit != null) {
                bestFit.cpuAvail -= reqCpu;
                bestFit.memAvail -= reqMem;
                rescheduledCount++;
            } else {
                unschedulableCount++;
            }
        }

        if (unschedulableCount > 0) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.CRITICAL)
                    .explanation(String.format("Node removal capacity shortfall: Evicting node '%s' displaces %d pods (requiring %s CPU, %s RAM), but only %d can be rescheduled on the remaining %d node(s). %d pod(s) would stay 'Pending'.",
                            targetNodeName, displacedPods.size(),
                            KubeQuantityParser.formatCpu(totalDisplacedCpu), KubeQuantityParser.formatMemory(totalDisplacedMem),
                            rescheduledCount, remainingBins.size(), unschedulableCount))
                    .suggestedFix(String.format("Scale the node group or reduce workloads before draining '%s' to ensure sufficient surplus capacity.",
                            targetNodeName))
                    .build());
        } else if (!displacedPods.isEmpty()) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.INFO)
                    .explanation(String.format("All %d displaced pod(s) on node '%s' can be safely rescheduled onto the %d remaining node(s).",
                            displacedPods.size(), targetNodeName, remainingBins.size()))
                    .suggestedFix("Proceed with standard cordon and drain procedure.")
                    .build());
        }

        return findings;
    }
}
