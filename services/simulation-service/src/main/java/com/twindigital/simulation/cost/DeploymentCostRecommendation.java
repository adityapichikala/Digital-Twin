package com.twindigital.simulation.cost;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentCostRecommendation {

    private String deploymentName;
    private int replicas;

    // Current allocation
    private String currentCpu;
    private String currentMemory;
    private double currentMonthlyCost;

    // Optimized suggestion
    private String suggestedCpu;
    private String suggestedMemory;
    private double suggestedMonthlyCost;

    // Financial impact
    private double estimatedMonthlySavings;
    private double savingsPercentage;
    private String recommendationReason;
}
