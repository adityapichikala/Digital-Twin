package com.twindigital.simulation.cost;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cost")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CostOptimizationController {

    private final CostOptimizationService optimizationService;
    private final CostModelProperties pricingProperties;

    /**
     * POST /api/v1/cost/optimize-deployment
     * Evaluates CPU and Memory requests for a single deployment and suggests right-sized requests and monthly savings.
     */
    @PostMapping(
            value = "/optimize-deployment",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DeploymentCostRecommendation> optimizeDeployment(
            @RequestBody DeploymentCostInput input) {

        if (input.getCpuRequest() == null || input.getMemoryRequest() == null) {
            return ResponseEntity.badRequest().build();
        }

        DeploymentCostRecommendation recommendation = optimizationService.optimizeDeployment(input);
        return ResponseEntity.ok(recommendation);
    }

    /**
     * POST /api/v1/cost/optimize-cluster
     * Batch evaluates multiple deployments and calculates total cluster-wide monthly savings.
     */
    @PostMapping(
            value = "/optimize-cluster",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ClusterOptimizationReport> optimizeCluster(
            @RequestBody List<DeploymentCostInput> inputs) {

        ClusterOptimizationReport report = optimizationService.optimizeCluster(inputs);
        return ResponseEntity.ok(report);
    }

    /**
     * GET /api/v1/cost/pricing-model
     * Returns the currently active pricing rates and right-sizing parameters.
     */
    @GetMapping(value = "/pricing-model", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CostModelProperties> getPricingModel() {
        return ResponseEntity.ok(pricingProperties);
    }
}
