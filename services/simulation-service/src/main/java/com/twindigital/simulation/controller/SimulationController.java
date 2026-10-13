package com.twindigital.simulation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Placeholder simulation endpoint.
 * The full implementation accepts a SimulationRequest (what-if changes)
 * and returns a SimulationResult (risk score, cost delta, explanations).
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> runSimulation(
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.accepted().body(Map.of(
                "simulationId", UUID.randomUUID().toString(),
                "status", "QUEUED",
                "message", "Simulation accepted. Full engine will process changes here."
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSimulation(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "simulationId", id,
                "status", "PENDING",
                "message", "Result polling will be implemented here"
        ));
    }
}
