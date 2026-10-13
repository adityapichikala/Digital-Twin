package com.twindigital.topology.controller;

import com.twindigital.topology.model.TopologyDTOs.BlastRadiusResponse;
import com.twindigital.topology.model.TopologyDTOs.TopologyResponse;
import com.twindigital.topology.service.TopologyGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TopologyController {

    private final TopologyGraphService topologyGraphService;

    /**
     * Returns full directed topology graph:
     * Node > Pod > Deployment > Service edges, plus service-to-service dependencies,
     * cluster health summary, and staleness information.
     */
    @GetMapping({"/api/topology", "/api/v1/topology", "/api/v1/topology/graph"})
    public ResponseEntity<TopologyResponse> getTopology(
            @RequestParam(value = "cluster", required = false) String cluster) {
        log.debug("Fetching topology graph for cluster: {}", cluster);
        TopologyResponse response = topologyGraphService.getTopology(cluster);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the blast radius for a given target service:
     * Upstream affected callers, downstream dependencies, impacted node/edge IDs,
     * and calculated risk score.
     */
    @GetMapping({"/api/topology/blast-radius", "/api/v1/topology/blast-radius"})
    public ResponseEntity<?> getBlastRadius(
            @RequestParam("target") String target,
            @RequestParam(value = "cluster", required = false) String cluster) {
        if (target == null || target.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing required query parameter 'target'"
            ));
        }

        log.debug("Calculating blast radius for service: {} in cluster: {}", target, cluster);
        BlastRadiusResponse response = topologyGraphService.calculateBlastRadius(cluster, target);
        return ResponseEntity.ok(response);
    }
}
