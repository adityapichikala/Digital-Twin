package com.twindigital.costrisk.service;

import com.twindigital.costrisk.client.AwsCostExplorerClient;
import com.twindigital.costrisk.client.AwsCostExplorerClient.AwsCostData;
import com.twindigital.costrisk.client.PrometheusClient;
import com.twindigital.costrisk.entity.CostRiskEntities.*;
import com.twindigital.costrisk.model.CostRiskDTOs.*;
import com.twindigital.costrisk.repository.CostRiskRepositories.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CostRiskServiceTest {

    @Mock
    private PrometheusClient prometheusClient;

    @Mock
    private AwsCostExplorerClient awsCostExplorerClient;

    @Mock
    private ClusterNodeRepository nodeRepository;

    @Mock
    private ClusterPodRepository podRepository;

    @Mock
    private ClusterDeploymentRepository deploymentRepository;

    @Mock
    private InstancePricingRepository pricingRepository;

    private SimpleMeterRegistry meterRegistry;
    private CostRiskService costRiskService;

    private static final String CLUSTER = "twindigital-cluster";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        costRiskService = new CostRiskService(
                prometheusClient,
                awsCostExplorerClient,
                nodeRepository,
                podRepository,
                deploymentRepository,
                pricingRepository,
                meterRegistry
        );
        ReflectionTestUtils.setField(costRiskService, "awsRegion", "ap-south-1");
        ReflectionTestUtils.setField(costRiskService, "defaultHourlyNodeCost", 0.0416);
        ReflectionTestUtils.setField(costRiskService, "monthlyHours", 730.0);
        costRiskService.initMetrics();
    }

    @Test
    @DisplayName("Should detect over-provisioned pods by comparing PromQL p95 usage with requests and right-size them")
    void shouldDetectOverprovisionedPodsAndRecommendRightSizing() {
        // Given pod requesting 500m CPU and 1024Mi RAM
        ClusterPodEntity pod = ClusterPodEntity.builder()
                .clusterName(CLUSTER)
                .namespace("twindigital")
                .name("billing-service-pod-1")
                .ownerName("billing-service")
                .cpuRequests("500m")
                .memoryRequests("1024Mi")
                .build();

        when(podRepository.findByClusterName(CLUSTER)).thenReturn(List.of(pod));

        // Mock Prometheus p95 usage: actual CPU is only 45m (9% util), memory is only 180Mi (17% util)
        when(prometheusClient.queryPodCpuUsageP95()).thenReturn(Map.of("billing-service-pod-1", 45.0));
        when(prometheusClient.queryPodMemoryUsageP95()).thenReturn(Map.of("billing-service-pod-1", 180L * 1024L * 1024L));

        // When
        OverprovisionedReport report = costRiskService.getOverprovisionedReport(CLUSTER);

        // Then
        assertThat(report).isNotNull();
        assertThat(report.getTotalOverprovisionedPods()).isEqualTo(1);
        assertThat(report.getPotentialMonthlySavingsUsd()).isGreaterThan(0.0);

        OverprovisionedPod item = report.getOverprovisionedPods().get(0);
        assertThat(item.getPodName()).isEqualTo("billing-service-pod-1");
        assertThat(item.getCurrentCpuRequest()).isEqualTo("500m");
        assertThat(item.getP95CpuUsage()).isEqualTo("45m");
        // Recommended CPU should be ceil(45 * 1.25) = 57m
        assertThat(item.getRecommendedCpuRequest()).isEqualTo("57m");
        assertThat(item.getRecommendedMemoryRequest()).contains("234Mi");
        assertThat(item.getCpuWastePercent()).isGreaterThan(85.0);

        // Verify Micrometer gauge updated
        double gaugeValue = meterRegistry.get("twin_overprovisioned_pods").gauge().value();
        assertThat(gaugeValue).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should fetch cost summary from AWS Cost Explorer when available")
    void shouldFetchCostSummaryFromAwsCostExplorer() {
        // Given live AWS Cost Explorer data
        AwsCostData awsData = AwsCostData.builder()
                .totalMonthlyCostUsd(145.80)
                .costByService(Map.of("Amazon Elastic Compute Cloud - Compute", 120.0, "Amazon Elastic Kubernetes Service", 25.80))
                .build();

        when(awsCostExplorerClient.getMonthlyCost(CLUSTER)).thenReturn(Optional.of(awsData));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        // When
        CostSummaryResponse summary = costRiskService.getCostSummary(CLUSTER);

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getDataSource()).isEqualTo("AWS_COST_EXPLORER");
        assertThat(summary.getTotalMonthlyCostUsd()).isEqualTo(145.80);
        assertThat(summary.getTotalAnnualCostUsd()).isEqualTo(145.80 * 12.0);

        // Verify Micrometer gauge updated
        double gaugeValue = meterRegistry.get("twin_cost_estimated_usd").gauge().value();
        assertThat(gaugeValue).isEqualTo(145.80);
    }

    @Test
    @DisplayName("Should gracefully fallback to instance pricing catalog when AWS Cost Explorer is offline")
    void shouldFallbackToPricingCatalogWhenAwsOffline() {
        // Given AWS Cost Explorer returns empty (offline mode)
        when(awsCostExplorerClient.getMonthlyCost(CLUSTER)).thenReturn(Optional.empty());

        ClusterNodeEntity node1 = ClusterNodeEntity.builder().clusterName(CLUSTER).name("node-1").build();
        ClusterNodeEntity node2 = ClusterNodeEntity.builder().clusterName(CLUSTER).name("node-2").build();
        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));

        // Instance pricing: 2 nodes * $0.0416/hr * 730 hrs = $60.74/mo
        when(pricingRepository.findByRegionAndInstanceType("ap-south-1", "t3.medium"))
                .thenReturn(Optional.of(InstancePricingEntity.builder().onDemandPrice(0.0416).build()));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        // When
        CostSummaryResponse summary = costRiskService.getCostSummary(CLUSTER);

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getDataSource()).isEqualTo("INSTANCE_PRICING_CATALOG");
        assertThat(summary.getTotalMonthlyCostUsd()).isEqualTo(60.74);
    }

    @Test
    @DisplayName("Should estimate monthly and annual cost delta for a proposed change set")
    void shouldEstimateCostDeltaForChange() {
        EstimateChangeRequest request = EstimateChangeRequest.builder()
                .kind("SCALE")
                .target("billing-service")
                .currentReplicas(2)
                .replicas(4) // scale from 2 to 4 pods
                .cpuRequest("200m")
                .memRequest("512Mi")
                .build();

        EstimateChangeResponse response = costRiskService.estimateChangeCost(request);

        assertThat(response).isNotNull();
        assertThat(response.getTarget()).isEqualTo("billing-service");
        assertThat(response.getDeltaMonthlyUsd()).isGreaterThan(0.0);
        assertThat(response.getDeltaAnnualUsd()).isEqualTo(response.getDeltaMonthlyUsd() * 12.0);
        assertThat(response.getExplanation()).contains("billing-service");
    }
}
