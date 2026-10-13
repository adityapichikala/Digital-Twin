package com.twindigital.costrisk.controller;

import com.twindigital.costrisk.model.CostRiskDTOs.*;
import com.twindigital.costrisk.service.CostRiskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CostRiskController.class)
class CostRiskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CostRiskService costRiskService;

    @Test
    @DisplayName("GET /api/cost/summary should return cluster cost summary")
    void shouldReturnCostSummary() throws Exception {
        CostSummaryResponse response = CostSummaryResponse.builder()
                .totalMonthlyCostUsd(60.74)
                .totalAnnualCostUsd(728.88)
                .currency("USD")
                .dataSource("INSTANCE_PRICING_CATALOG")
                .potentialMonthlySavingsUsd(14.50)
                .overprovisionedPodCount(2)
                .byNamespace(List.of(NamespaceCost.builder()
                        .namespace("twindigital")
                        .monthlyCostUsd(60.74)
                        .percentOfTotal(100.0)
                        .podCount(4)
                        .build()))
                .lastUpdated(Instant.now())
                .build();

        when(costRiskService.getCostSummary(any())).thenReturn(response);

        mockMvc.perform(get("/api/cost/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMonthlyCostUsd").value(60.74))
                .andExpect(jsonPath("$.dataSource").value("INSTANCE_PRICING_CATALOG"))
                .andExpect(jsonPath("$.potentialMonthlySavingsUsd").value(14.50))
                .andExpect(jsonPath("$.overprovisionedPodCount").value(2));
    }

    @Test
    @DisplayName("GET /api/cost/overprovisioned should return overprovisioned pods and recommendations")
    void shouldReturnOverprovisionedPods() throws Exception {
        OverprovisionedPod pod = OverprovisionedPod.builder()
                .podName("billing-service-pod-1")
                .namespace("twindigital")
                .serviceName("billing-service")
                .currentCpuRequest("500m")
                .p95CpuUsage("45m")
                .recommendedCpuRequest("57m")
                .currentMemoryRequest("1024Mi")
                .p95MemoryUsage("180Mi")
                .recommendedMemoryRequest("234Mi")
                .cpuWastePercent(91.0)
                .estimatedMonthlyWasteUsd(7.25)
                .build();

        OverprovisionedReport report = OverprovisionedReport.builder()
                .totalPodsAnalyzed(4)
                .totalOverprovisionedPods(1)
                .potentialMonthlySavingsUsd(7.25)
                .potentialAnnualSavingsUsd(87.0)
                .overprovisionedPods(List.of(pod))
                .evaluatedWindow("7-day p95 PromQL")
                .evaluatedAt(Instant.now())
                .build();

        when(costRiskService.getOverprovisionedReport(any())).thenReturn(report);

        mockMvc.perform(get("/api/cost/overprovisioned")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOverprovisionedPods").value(1))
                .andExpect(jsonPath("$.potentialMonthlySavingsUsd").value(7.25))
                .andExpect(jsonPath("$.overprovisionedPods[0].podName").value("billing-service-pod-1"))
                .andExpect(jsonPath("$.overprovisionedPods[0].recommendedCpuRequest").value("57m"));
    }

    @Test
    @DisplayName("POST /api/cost/estimate-change should return cost delta")
    void shouldEstimateChange() throws Exception {
        EstimateChangeResponse response = EstimateChangeResponse.builder()
                .target("billing-service")
                .baselineMonthlyCostUsd(6.07)
                .proposedMonthlyCostUsd(12.14)
                .deltaMonthlyUsd(6.07)
                .deltaAnnualUsd(72.84)
                .explanation("Scaling billing-service adds +$6.07/month")
                .build();

        when(costRiskService.estimateChangeCost(any())).thenReturn(response);

        String json = """
                {
                    "kind": "SCALE",
                    "target": "billing-service",
                    "replicas": 4,
                    "cpuRequest": "200m",
                    "memRequest": "512Mi"
                }
                """;

        mockMvc.perform(post("/api/cost/estimate-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value("billing-service"))
                .andExpect(jsonPath("$.deltaMonthlyUsd").value(6.07))
                .andExpect(jsonPath("$.deltaAnnualUsd").value(72.84));
    }
}
