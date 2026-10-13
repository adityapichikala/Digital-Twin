package com.twindigital.clustersync.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Placeholder sync-status endpoint.
 * The full implementation will expose last-sync timestamp, node count, etc.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "service", "cluster-sync-service",
                "status", "operational",
                "message", "Last sync status will be reported here"
        ));
    }
}
