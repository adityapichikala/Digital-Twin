package com.twindigital.simulation.check;

import com.twindigital.simulation.context.SimulationContext;
import com.twindigital.simulation.entity.SimulationEntities.ClusterHpaEntity;
import com.twindigital.simulation.entity.SimulationEntities.ClusterPodEntity;
import com.twindigital.simulation.model.SimulationModels.ChangeKind;
import com.twindigital.simulation.model.SimulationModels.ChangeSet;
import com.twindigital.simulation.model.SimulationModels.FindingSeverity;
import com.twindigital.simulation.model.SimulationModels.SimulationFinding;
import com.twindigital.simulation.util.KubeQuantityParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class HpaImpactCheck implements SimulationCheck {

    @Override
    public String getName() {
        return "HpaImpact";
    }

    @Override
    public List<SimulationFinding> evaluate(SimulationContext context) {
        List<SimulationFinding> findings = new ArrayList<>();
        ChangeSet cs = context.getChangeSet();
        if (cs == null || cs.getTarget() == null) return findings;

        String target = cs.getTarget();
        Optional<ClusterHpaEntity> hpaOpt = context.findHpaForTarget(target);
        if (hpaOpt.isEmpty()) {
            return findings;
        }

        ClusterHpaEntity hpa = hpaOpt.get();

        // 1. Determine per-pod resource requests for the target
        long perPodCpu = KubeQuantityParser.parseCpuMillis(cs.getCpuRequest());
        long perPodMem = KubeQuantityParser.parseMemoryBytes(cs.getMemRequest());

        if (perPodCpu == 0L || perPodMem == 0L) {
            List<ClusterPodEntity> existingPods = context.getPodsForDeployment(target);
            if (!existingPods.isEmpty()) {
                if (perPodCpu == 0L) {
                    perPodCpu = KubeQuantityParser.parseCpuMillis(existingPods.get(0).getCpuRequests());
                }
                if (perPodMem == 0L) {
                    perPodMem = KubeQuantityParser.parseMemoryBytes(existingPods.get(0).getMemoryRequests());
                }
            }
        }

        int maxReplicas = hpa.getMaxReplicas();
        long totalClusterCpu = context.getTotalClusterAllocatableCpu();
        long totalClusterMem = context.getTotalClusterAllocatableMem();

        // 2. Check feasibility at HPA maxReplicas against total cluster capacity
        if (perPodCpu > 0 && totalClusterCpu > 0) {
            long totalRequiredCpuAtMax = maxReplicas * perPodCpu;
            if (totalRequiredCpuAtMax > totalClusterCpu) {
                int maxFeasibleReplicas = (int) (totalClusterCpu / perPodCpu);
                findings.add(SimulationFinding.builder()
                        .check(getName())
                        .severity(FindingSeverity.HIGH)
                        .explanation(String.format("HPA maxReplicas capacity shortfall: Under peak load, HPA '%s' can scale target '%s' to %d replicas (requiring %s CPU), but total cluster allocatable CPU is only %s. Autoscaling will stall at %d replicas, leaving remaining pods in 'Pending' state.",
                                hpa.getName(), target, maxReplicas,
                                KubeQuantityParser.formatCpu(totalRequiredCpuAtMax),
                                KubeQuantityParser.formatCpu(totalClusterCpu),
                                maxFeasibleReplicas))
                        .suggestedFix(String.format("Configure Cluster Autoscaler / Karpenter to dynamically add nodes during peak traffic, or lower HPA maxReplicas to %d.",
                                maxFeasibleReplicas))
                        .build());
            }
        }

        if (perPodMem > 0 && totalClusterMem > 0) {
            long totalRequiredMemAtMax = maxReplicas * perPodMem;
            if (totalRequiredMemAtMax > totalClusterMem) {
                findings.add(SimulationFinding.builder()
                        .check(getName())
                        .severity(FindingSeverity.HIGH)
                        .explanation(String.format("Under peak traffic, HPA '%s' requires %s RAM at maxReplicas (%d), which exceeds total cluster allocatable RAM (%s).",
                                hpa.getName(), KubeQuantityParser.formatMemory(totalRequiredMemAtMax),
                                maxReplicas, KubeQuantityParser.formatMemory(totalClusterMem)))
                        .suggestedFix(String.format("Lower HPA maxReplicas or configure autoscaling node groups with greater RAM capacity."))
                        .build());
            }
        }

        // 3. Check proposed fixed replicas vs HPA min/max bounds
        if (cs.getKind() == ChangeKind.SCALE && cs.getReplicas() != null) {
            int proposed = cs.getReplicas();
            Integer minReplicas = hpa.getMinReplicas() != null ? hpa.getMinReplicas() : 1;

            if (proposed < minReplicas) {
                findings.add(SimulationFinding.builder()
                        .check(getName())
                        .severity(FindingSeverity.MEDIUM)
                        .explanation(String.format("Proposed replica count (%d) is below HPA minReplicas (%d) for '%s'. The HPA controller will immediately override this change and scale back up to %d replicas.",
                                proposed, minReplicas, hpa.getName(), minReplicas))
                        .suggestedFix(String.format("Update HPA '%s' minReplicas to %d or delete the HPA before manual scale-down.",
                                hpa.getName(), proposed))
                        .build());
            } else if (proposed > maxReplicas) {
                findings.add(SimulationFinding.builder()
                        .check(getName())
                        .severity(FindingSeverity.MEDIUM)
                        .explanation(String.format("Proposed replica count (%d) exceeds HPA maxReplicas (%d) for '%s'. The HPA controller may scale down replicas on the next evaluation window.",
                                proposed, maxReplicas, hpa.getName()))
                        .suggestedFix(String.format("Increase HPA '%s' maxReplicas to at least %d.",
                                hpa.getName(), proposed))
                        .build());
            }
        }

        return findings;
    }
}
