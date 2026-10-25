package com.twindigital.simulation.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CostOptimizationServiceTest {

    private CostOptimizationService optimizationService;
    private CostModelProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CostModelProperties();
        properties.setVcpuHourlyRate(0.0316);
        properties.setMemoryGbHourlyRate(0.0042);
        properties.setMonthlyHours(730.0);
        properties.setSafetyHeadroom(1.20);
        properties.setMinCpuMillicores(50);
        properties.setMinMemoryMib(64);

        optimizationService = new CostOptimizationService(properties);
    }

    @Test
    void testOptimizeDeploymentWithoutP95() {
        DeploymentCostInput input = DeploymentCostInput.builder()
                .deploymentName("billing-service")
                .cpuRequest("1000m")
                .memoryRequest("2048Mi")
                .replicas(2)
                .build();

        DeploymentCostRecommendation rec = optimizationService.optimizeDeployment(input);

        assertNotNull(rec);
        assertEquals("billing-service", rec.getDeploymentName());
        assertEquals("1000m", rec.getCurrentCpu());
        assertEquals("2048Mi", rec.getCurrentMemory());

        // Right-sized values should be less than current allocation
        assertTrue(rec.getSuggestedMonthlyCost() < rec.getCurrentMonthlyCost());
        assertTrue(rec.getEstimatedMonthlySavings() > 0.0);
        assertTrue(rec.getSavingsPercentage() > 0.0);
        assertEquals("540m", rec.getSuggestedCpu());     // 1000 * 0.45 * 1.20 = 540m
        assertEquals("1229Mi", rec.getSuggestedMemory()); // 2048 * 0.50 * 1.20 = 1228.8Mi -> 1229Mi
    }

    @Test
    void testOptimizeDeploymentWithObservedP95() {
        DeploymentCostInput input = DeploymentCostInput.builder()
                .deploymentName("order-service")
                .cpuRequest("2000m")
                .memoryRequest("4096Mi")
                .observedP95Cpu("400m")
                .observedP95Memory("800Mi")
                .replicas(3)
                .build();

        DeploymentCostRecommendation rec = optimizationService.optimizeDeployment(input);

        assertNotNull(rec);
        assertEquals("order-service", rec.getDeploymentName());
        assertEquals("480m", rec.getSuggestedCpu()); // 400 * 1.20 = 480m
        assertEquals("960Mi", rec.getSuggestedMemory()); // 800 * 1.20 = 960Mi
        assertTrue(rec.getEstimatedMonthlySavings() > 50.0);
    }

    @Test
    void testClusterBatchOptimization() {
        DeploymentCostInput d1 = DeploymentCostInput.builder()
                .deploymentName("frontend")
                .cpuRequest("500m")
                .memoryRequest("1024Mi")
                .replicas(2)
                .build();

        DeploymentCostInput d2 = DeploymentCostInput.builder()
                .deploymentName("backend")
                .cpuRequest("1500m")
                .memoryRequest("3072Mi")
                .replicas(2)
                .build();

        ClusterOptimizationReport report = optimizationService.optimizeCluster(List.of(d1, d2));

        assertNotNull(report);
        assertEquals(2, report.getTotalDeploymentsAnalyzed());
        assertTrue(report.getCurrentTotalMonthlyCost() > report.getSuggestedTotalMonthlyCost());
        assertTrue(report.getTotalEstimatedMonthlySavings() > 0.0);
        assertTrue(report.getOverallSavingsPercentage() > 0.0);
    }
}
