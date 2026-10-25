package com.twindigital.simulation.cost;

import com.twindigital.simulation.util.KubeQuantityParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CostOptimizationService {

    private final CostModelProperties pricingProperties;

    /**
     * Optimizes a single deployment allocation and calculates projected monthly savings.
     */
    public DeploymentCostRecommendation optimizeDeployment(DeploymentCostInput input) {
        int replicas = Math.max(1, input.getReplicas());

        long currentCpuMilli = KubeQuantityParser.parseCpuMillis(input.getCpuRequest());
        long currentMemMib = KubeQuantityParser.parseMemoryBytes(input.getMemoryRequest()) / (1024 * 1024);

        // 1. Right-Sizing Logic (Compute Recommended Requests)
        long suggestedCpuMilli;
        long suggestedMemMib;
        String reason;

        if (input.getObservedP95Cpu() != null && !input.getObservedP95Cpu().isBlank()) {
            long p95Cpu = KubeQuantityParser.parseCpuMillis(input.getObservedP95Cpu());
            suggestedCpuMilli = Math.max(
                    pricingProperties.getMinCpuMillicores(),
                    Math.round(p95Cpu * pricingProperties.getSafetyHeadroom())
            );
            reason = "Right-sized against observed 7-day p95 metrics + " +
                    (int) ((pricingProperties.getSafetyHeadroom() - 1.0) * 100) + "% safety headroom.";
        } else {
            // Standard over-provisioning heuristic (typical cloud workloads request 2x-3x actual need)
            suggestedCpuMilli = Math.max(
                    pricingProperties.getMinCpuMillicores(),
                    Math.round(currentCpuMilli * 0.45 * pricingProperties.getSafetyHeadroom())
            );
            reason = "Workload right-sized based on average cloud over-provisioning benchmark with " +
                    (int) ((pricingProperties.getSafetyHeadroom() - 1.0) * 100) + "% headroom.";
        }

        if (input.getObservedP95Memory() != null && !input.getObservedP95Memory().isBlank()) {
            long p95Mem = KubeQuantityParser.parseMemoryBytes(input.getObservedP95Memory()) / (1024 * 1024);
            suggestedMemMib = Math.max(
                    pricingProperties.getMinMemoryMib(),
                    Math.round(p95Mem * pricingProperties.getSafetyHeadroom())
            );
        } else {
            suggestedMemMib = Math.max(
                    pricingProperties.getMinMemoryMib(),
                    Math.round(currentMemMib * 0.50 * pricingProperties.getSafetyHeadroom())
            );
        }

        // Cap suggestions so they never exceed current requests
        suggestedCpuMilli = Math.min(suggestedCpuMilli, currentCpuMilli);
        suggestedMemMib = Math.min(suggestedMemMib, currentMemMib);

        // 2. Financial Pricing Model Calculations
        double currentMonthly = calculateMonthlyCost(currentCpuMilli, currentMemMib, replicas);
        double suggestedMonthly = calculateMonthlyCost(suggestedCpuMilli, suggestedMemMib, replicas);
        double savings = Math.max(0.0, currentMonthly - suggestedMonthly);
        double savingsPercent = currentMonthly > 0 ? (savings / currentMonthly) * 100.0 : 0.0;

        return DeploymentCostRecommendation.builder()
                .deploymentName(input.getDeploymentName() != null ? input.getDeploymentName() : "unknown-deployment")
                .replicas(replicas)
                .currentCpu(currentCpuMilli + "m")
                .currentMemory(currentMemMib + "Mi")
                .currentMonthlyCost(round(currentMonthly))
                .suggestedCpu(suggestedCpuMilli + "m")
                .suggestedMemory(suggestedMemMib + "Mi")
                .suggestedMonthlyCost(round(suggestedMonthly))
                .estimatedMonthlySavings(round(savings))
                .savingsPercentage(round(savingsPercent))
                .recommendationReason(reason)
                .build();
    }

    /**
     * Batch evaluates multiple deployments and produces an aggregated cluster report.
     */
    public ClusterOptimizationReport optimizeCluster(List<DeploymentCostInput> inputs) {
        List<DeploymentCostRecommendation> recs = new ArrayList<>();
        double totalCurrent = 0.0;
        double totalSuggested = 0.0;

        for (DeploymentCostInput input : inputs) {
            DeploymentCostRecommendation rec = optimizeDeployment(input);
            recs.add(rec);
            totalCurrent += rec.getCurrentMonthlyCost();
            totalSuggested += rec.getSuggestedMonthlyCost();
        }

        double totalSavings = Math.max(0.0, totalCurrent - totalSuggested);
        double overallSavingsPercent = totalCurrent > 0 ? (totalSavings / totalCurrent) * 100.0 : 0.0;

        return ClusterOptimizationReport.builder()
                .totalDeploymentsAnalyzed(inputs.size())
                .currentTotalMonthlyCost(round(totalCurrent))
                .suggestedTotalMonthlyCost(round(totalSuggested))
                .totalEstimatedMonthlySavings(round(totalSavings))
                .overallSavingsPercentage(round(overallSavingsPercent))
                .recommendations(recs)
                .build();
    }

    /**
     * Configurable Pricing Formula:
     * Cost = ((vCPU * vcpuRate) + (RAM_GB * ramRate)) * replicas * 730 hours
     */
    public double calculateMonthlyCost(long cpuMilli, long memMib, int replicas) {
        double vcpu = cpuMilli / 1000.0;
        double ramGb = memMib / 1024.0;

        double hourlyPerReplica = (vcpu * pricingProperties.getVcpuHourlyRate()) +
                                  (ramGb * pricingProperties.getMemoryGbHourlyRate());

        return hourlyPerReplica * replicas * pricingProperties.getMonthlyHours();
    }

    private double round(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
