package com.twindigital.simulation.check;

import com.twindigital.simulation.context.SimulationContext;
import com.twindigital.simulation.model.SimulationModels.ChangeKind;
import com.twindigital.simulation.model.SimulationModels.ChangeSet;
import com.twindigital.simulation.model.SimulationModels.FindingSeverity;
import com.twindigital.simulation.model.SimulationModels.SimulationFinding;
import com.twindigital.simulation.util.KubeQuantityParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LimitSanityCheck implements SimulationCheck {

    @Override
    public String getName() {
        return "LimitSanity";
    }

    @Override
    public List<SimulationFinding> evaluate(SimulationContext context) {
        List<SimulationFinding> findings = new ArrayList<>();
        ChangeSet cs = context.getChangeSet();
        if (cs == null) return findings;

        // Limit sanity applies primarily to RESOURCE_CHANGE and NEW_DEPLOYMENT
        if (cs.getKind() != ChangeKind.RESOURCE_CHANGE && cs.getKind() != ChangeKind.NEW_DEPLOYMENT) {
            return findings;
        }

        long reqCpu = KubeQuantityParser.parseCpuMillis(cs.getCpuRequest());
        long limCpu = KubeQuantityParser.parseCpuMillis(cs.getCpuLimit());
        long reqMem = KubeQuantityParser.parseMemoryBytes(cs.getMemRequest());
        long limMem = KubeQuantityParser.parseMemoryBytes(cs.getMemLimit());

        // 1. Check: limit < request
        if (limCpu > 0 && reqCpu > 0 && limCpu < reqCpu) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.CRITICAL)
                    .explanation(String.format("CPU limit (%s) is strictly less than CPU request (%s). Kubernetes rejects invalid limit < request specifications, or the container will experience immediate throttling.",
                            KubeQuantityParser.formatCpu(limCpu), KubeQuantityParser.formatCpu(reqCpu)))
                    .suggestedFix(String.format("Increase CPU limit to at least %s or decrease the CPU request.",
                            KubeQuantityParser.formatCpu(reqCpu)))
                    .build());
        }

        if (limMem > 0 && reqMem > 0 && limMem < reqMem) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.CRITICAL)
                    .explanation(String.format("Memory limit (%s) is less than memory request (%s). Containers will be OOMKilled immediately upon exceeding the limit.",
                            KubeQuantityParser.formatMemory(limMem), KubeQuantityParser.formatMemory(reqMem)))
                    .suggestedFix(String.format("Increase memory limit to at least %s or decrease the memory request.",
                            KubeQuantityParser.formatMemory(reqMem)))
                    .build());
        }

        // 2. Check: request greater than largest node
        long largestNodeCpu = context.getLargestNodeAllocatableCpu();
        if (reqCpu > 0 && largestNodeCpu > 0 && reqCpu > largestNodeCpu) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.CRITICAL)
                    .explanation(String.format("Requested CPU (%s) exceeds the allocatable capacity of the largest worker node in the cluster (%s). A single pod cannot span multiple nodes, so this pod will never be scheduled.",
                            KubeQuantityParser.formatCpu(reqCpu), KubeQuantityParser.formatCpu(largestNodeCpu)))
                    .suggestedFix(String.format("Lower CPU request below %s or scale the node group to larger instance types (e.g., t3.xlarge or m5.xlarge).",
                            KubeQuantityParser.formatCpu(largestNodeCpu)))
                    .build());
        }

        long largestNodeMem = context.getLargestNodeAllocatableMem();
        if (reqMem > 0 && largestNodeMem > 0 && reqMem > largestNodeMem) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.CRITICAL)
                    .explanation(String.format("Requested memory (%s) exceeds the allocatable capacity of the largest worker node in the cluster (%s). The pod cannot be placed on any existing node.",
                            KubeQuantityParser.formatMemory(reqMem), KubeQuantityParser.formatMemory(largestNodeMem)))
                    .suggestedFix(String.format("Lower memory request below %s or provision nodes with larger RAM capacity.",
                            KubeQuantityParser.formatMemory(largestNodeMem)))
                    .build());
        }

        // 3. Check: missing limits
        boolean missingMemLimit = (cs.getMemLimit() == null || cs.getMemLimit().isBlank());
        if (missingMemLimit && reqMem > 0) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.MEDIUM)
                    .explanation("Memory limit is not specified. An unconstrained memory leak in the container can consume the entire host node's RAM and trigger kernel OOM killer against neighbor pods.")
                    .suggestedFix(String.format("Set memory limit to 1.5x - 2x of memory request (e.g. %s).",
                            KubeQuantityParser.formatMemory(reqMem * 2)))
                    .build());
        }

        boolean missingCpuLimit = (cs.getCpuLimit() == null || cs.getCpuLimit().isBlank());
        if (missingCpuLimit && reqCpu > 0) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.LOW)
                    .explanation("CPU limit is not configured. Container may consume burstable CPU cycles under load without a hard cap.")
                    .suggestedFix("Configure a CPU limit if strict CPU isolation is required.")
                    .build());
        }

        // 4. Check: extreme ratios
        if (limCpu > 0 && reqCpu > 0) {
            double cpuRatio = (double) limCpu / (double) reqCpu;
            if (cpuRatio >= 10.0) {
                findings.add(SimulationFinding.builder()
                        .check(getName())
                        .severity(FindingSeverity.MEDIUM)
                        .explanation(String.format("CPU limit-to-request ratio is %.0f:1 (request: %s, limit: %s). An overcommit ratio greater than 10:1 creates severe throttling risk when multiple pods burst concurrently.",
                                cpuRatio, KubeQuantityParser.formatCpu(reqCpu), KubeQuantityParser.formatCpu(limCpu)))
                        .suggestedFix("Increase CPU request closer to expected baseline load (recommended ratio: 2:1 to 5:1).")
                        .build());
            }
        }

        if (limMem > 0 && reqMem > 0) {
            double memRatio = (double) limMem / (double) reqMem;
            if (memRatio >= 5.0) {
                findings.add(SimulationFinding.builder()
                        .check(getName())
                        .severity(FindingSeverity.LOW)
                        .explanation(String.format("Memory limit is %.1fx the request (%s vs %s), which may cause unexpected node evictions if multiple containers experience memory growth simultaneously.",
                                memRatio, KubeQuantityParser.formatMemory(limMem), KubeQuantityParser.formatMemory(reqMem)))
                        .suggestedFix("Set memory request closer to expected working set memory.")
                        .build());
            }
        }

        return findings;
    }
}
