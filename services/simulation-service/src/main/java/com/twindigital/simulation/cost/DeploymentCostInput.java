package com.twindigital.simulation.cost;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentCostInput {

    private String deploymentName;

    /**
     * Configured CPU request (e.g. "1000m", "1", "250m").
     */
    private String cpuRequest;

    /**
     * Configured Memory request (e.g. "2048Mi", "2Gi", "512Mi").
     */
    private String memoryRequest;

    /**
     * Desired replicas (default 1).
     */
    @Builder.Default
    private int replicas = 1;

    /**
     * Optional actual p95 CPU observed (e.g. "300m"). If omitted, an overprovisioning factor is applied.
     */
    private String observedP95Cpu;

    /**
     * Optional actual p95 Memory observed (e.g. "700Mi").
     */
    private String observedP95Memory;
}
