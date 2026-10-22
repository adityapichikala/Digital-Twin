package com.twindigital.costrisk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

public class CostRiskDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverprovisionedPod {
        private String podName;
        private String namespace;
        private String serviceName;
        private String nodeName;
        private String currentCpuRequest;       // e.g. "500m"
        private String p95CpuUsage;              // e.g. "45m"
        private String recommendedCpuRequest;    // e.g. "60m"
        private String currentMemoryRequest;    // e.g. "1024Mi"
        private String p95MemoryUsage;           // e.g. "180Mi"
        private String recommendedMemoryRequest; // e.g. "234Mi"
        private double cpuUtilizationRatio;      // e.g. 0.09 (9%)
        private double memoryUtilizationRatio;   // e.g. 0.17 (17%)
        private double cpuWastePercent;          // e.g. 91.0%
        private double memoryWastePercent;       // e.g. 82.5%
        private double estimatedMonthlyWasteUsd; // e.g. 14.20
        private String explanation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverprovisionedReport {
        private int totalPodsAnalyzed;
        private int totalOverprovisionedPods;
        private double potentialMonthlySavingsUsd;
        private double potentialAnnualSavingsUsd;
        @Builder.Default
        private List<OverprovisionedPod> overprovisionedPods = new ArrayList<>();
        private String evaluatedWindow;          // e.g. "7-day p95 PromQL"
        private Instant evaluatedAt;
        @Builder.Default
        private boolean dataAvailable = true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NamespaceCost {
        private String namespace;
        private double monthlyCostUsd;
        private double percentOfTotal;
        private int podCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceCost {
        private String serviceName;
        private String namespace;
        private double monthlyCostUsd;
        private double percentOfTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostSummaryResponse {
        private double totalMonthlyCostUsd;
        private double totalAnnualCostUsd;
        @Builder.Default
        private String currency = "USD";
        private String dataSource;               // "AWS_COST_EXPLORER" | "INSTANCE_PRICING_CATALOG"
        private double potentialMonthlySavingsUsd;
        private int overprovisionedPodCount;
        @Builder.Default
        private List<NamespaceCost> byNamespace = new ArrayList<>();
        @Builder.Default
        private List<ServiceCost> byService = new ArrayList<>();
        private Instant lastUpdated;
        @Builder.Default
        private boolean dataAvailable = true;
        @Builder.Default
        private double controlPlaneCostUsd = 73.00;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstimateChangeRequest {
        private String kind;                     // "RESOURCE_CHANGE", "SCALE", "NEW_DEPLOYMENT", "NODE_REMOVAL"
        private String target;                   // Deployment or Node name
        @Builder.Default
        private String namespace = "twindigital";
        private Integer replicas;
        private String cpuRequest;
        private String memRequest;
        private Integer currentReplicas;
        private String currentCpuRequest;
        private String currentMemRequest;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstimateChangeResponse {
        private String target;
        private double baselineMonthlyCostUsd;
        private double proposedMonthlyCostUsd;
        private double deltaMonthlyUsd;
        private double deltaAnnualUsd;
        private String explanation;
    }
}
