package com.twindigital.topology.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Placeholder topology graph endpoint.
 * The full implementation will return nodes and edges as a graph DTO.
 */
@RestController
@RequestMapping("/api/v1/topology")
public class TopologyController {

    @GetMapping("/graph")
    public ResponseEntity<Map<String, Object>> graph() {
        return ResponseEntity.ok(Map.of(
                "service", "topology-service",
                "nodes", java.util.List.of(),
                "edges", java.util.List.of(),
                "message", "Full graph topology will be returned here"
        ));
    }
}
