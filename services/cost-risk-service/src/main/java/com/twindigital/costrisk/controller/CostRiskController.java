package com.twindigital.costrisk.controller;

import com.twindigital.costrisk.model.CostRiskDTOs.*;
import com.twindigital.costrisk.service.CostRiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CostRiskController {

    private final CostRiskService costRiskService;

    /**
     * Returns cluster-wide monthly cost summary, attributing costs by namespace and service.
     */
    @GetMapping({"/api/cost/summary", "/api/v1/cost/summary"})
    public ResponseEntity<CostSummaryResponse> getCostSummary(
            @RequestParam(value = "cluster", required = false, defaultValue = "twindigital-cluster") String cluster) {
        log.debug("Fetching cost summary for cluster: {}", cluster);
        CostSummaryResponse summary = costRiskService.getCostSummary(cluster);
        return ResponseEntity.ok(summary);
    }

    /**
     * Returns over-provisioned pods flagged by comparing Prometheus 7-day p95 PromQL usage
     * against requests, with recommended right-sized values and potential monthly savings.
     */
    @GetMapping({"/api/cost/overprovisioned", "/api/v1/cost/overprovisioned"})
    public ResponseEntity<OverprovisionedReport> getOverprovisioned(
            @RequestParam(value = "cluster", required = false, defaultValue = "twindigital-cluster") String cluster) {
        log.debug("Fetching overprovisioned report for cluster: {}", cluster);
        OverprovisionedReport report = costRiskService.getOverprovisionedReport(cluster);
        return ResponseEntity.ok(report);
    }

    /**
     * Estimates monthly cost delta of a proposed change set (e.g. scale or resize).
     */
    @PostMapping({"/api/cost/estimate-change", "/api/v1/cost/estimate-change"})
    public ResponseEntity<EstimateChangeResponse> estimateChange(
            @RequestBody EstimateChangeRequest request) {
        log.debug("Estimating cost delta for target: {}", request.getTarget());
        EstimateChangeResponse response = costRiskService.estimateChangeCost(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Backward-compatible endpoint for simulation assessment integration.
     */
    @PostMapping({"/api/v1/assessments", "/api/assessments"})
    public ResponseEntity<Map<String, Object>> assess(
            @RequestBody Map<String, Object> body) {
        String target = body.containsKey("target") ? body.get("target").toString() : "workload";
        EstimateChangeRequest req = EstimateChangeRequest.builder()
                .target(target)
                .kind(body.containsKey("kind") ? body.get("kind").toString() : "RESOURCE_CHANGE")
                .replicas(body.containsKey("replicas") ? Integer.parseInt(body.get("replicas").toString()) : 2)
                .cpuRequest(body.containsKey("cpuRequest") ? body.get("cpuRequest").toString() : "200m")
                .memRequest(body.containsKey("memRequest") ? body.get("memRequest").toString() : "256Mi")
                .build();

        EstimateChangeResponse est = costRiskService.estimateChangeCost(req);

        return ResponseEntity.ok(Map.of(
                "costDeltaUsd", est.getDeltaMonthlyUsd(),
                "annualDeltaUsd", est.getDeltaAnnualUsd(),
                "baselineCostUsd", est.getBaselineMonthlyCostUsd(),
                "proposedCostUsd", est.getProposedMonthlyCostUsd(),
                "explanation", est.getExplanation()
        ));
    }
}
