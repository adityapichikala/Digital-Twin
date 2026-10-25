package com.twindigital.simulation.cost;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterOptimizationReport {

    private int totalDeploymentsAnalyzed;
    private double currentTotalMonthlyCost;
    private double suggestedTotalMonthlyCost;
    private double totalEstimatedMonthlySavings;
    private double overallSavingsPercentage;
    private List<DeploymentCostRecommendation> recommendations;
}
