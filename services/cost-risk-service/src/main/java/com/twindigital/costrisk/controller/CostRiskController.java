package com.twindigital.costrisk.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Placeholder cost/risk assessment endpoint.
 * The full implementation will compute cost delta and risk score
 * from a SimulationResult payload.
 */
@RestController
@RequestMapping("/api/v1/assessments")
public class CostRiskController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> assess(
            @RequestBody Map<String, Object> simulationResult) {
        return ResponseEntity.ok(Map.of(
                "costDeltaUsd", 0.0,
                "riskScore", 0,
                "riskLevel", "UNKNOWN",
                "explanations", java.util.List.of(),
                "message", "Full cost/risk engine will be implemented here"
        ));
    }
}
