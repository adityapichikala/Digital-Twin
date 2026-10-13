package com.twindigital.simulation.controller;

import com.twindigital.simulation.entity.SimulationEntities.SimulationAuditEntity;
import com.twindigital.simulation.model.SimulationModels.ChangeSet;
import com.twindigital.simulation.model.SimulationModels.SimulationRequest;
import com.twindigital.simulation.model.SimulationModels.SimulationResult;
import com.twindigital.simulation.repository.SimulationRepositories.SimulationAuditRepository;
import com.twindigital.simulation.service.SimulationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SimulationController {

    private final SimulationEngine simulationEngine;
    private final SimulationAuditRepository auditRepository;

    /**
     * Executes a what-if simulation against real synced cluster state.
     */
    @PostMapping({"/api/simulations", "/api/v1/simulations", "/api/v1/simulations/run"})
    public ResponseEntity<SimulationResult> runSimulation(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {

        String user = headerUserId != null && !headerUserId.isBlank() ? headerUserId : "anonymous";
        String cluster = "twindigital-cluster";

        if (body.containsKey("user") && body.get("user") != null) {
            user = body.get("user").toString();
        }
        if (body.containsKey("clusterName") && body.get("clusterName") != null) {
            cluster = body.get("clusterName").toString();
        }

        ChangeSet changeSet;
        if (body.containsKey("changeSet") && body.get("changeSet") instanceof Map<?, ?> csMap) {
            changeSet = mapToChangeSet(csMap);
        } else {
            // Body itself is the change set
            changeSet = mapToChangeSet(body);
        }

        SimulationRequest request = SimulationRequest.builder()
                .clusterName(cluster)
                .user(user)
                .changeSet(changeSet)
                .build();

        log.info("Running what-if simulation: user={} kind={} target={}",
                user, changeSet.getKind(), changeSet.getTarget());

        SimulationResult result = simulationEngine.runSimulation(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Fetches a past simulation result by ID from the audit log.
     */
    @GetMapping({"/api/simulations/{id}", "/api/v1/simulations/{id}"})
    public ResponseEntity<?> getSimulation(@PathVariable String id) {
        try {
            UUID simUuid = UUID.fromString(id);
            Optional<SimulationAuditEntity> auditOpt = auditRepository.findById(simUuid);
            if (auditOpt.isPresent()) {
                return ResponseEntity.ok(auditOpt.get());
            }

            // Also check by simulation_id
            List<SimulationAuditEntity> list = auditRepository.findAll().stream()
                    .filter(a -> a.getSimulationId().toString().equalsIgnoreCase(id))
                    .toList();
            if (!list.isEmpty()) {
                return ResponseEntity.ok(list.get(0));
            }

            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid UUID format: " + id));
        }
    }

    /**
     * Returns history of simulations executed for the cluster.
     */
    @GetMapping({"/api/simulations", "/api/v1/simulations"})
    public ResponseEntity<List<SimulationAuditEntity>> listSimulations(
            @RequestParam(value = "cluster", required = false, defaultValue = "twindigital-cluster") String cluster) {
        List<SimulationAuditEntity> history = auditRepository.findByClusterNameOrderByExecutedAtDesc(cluster);
        return ResponseEntity.ok(history);
    }

    private ChangeSet mapToChangeSet(Map<?, ?> map) {
        ChangeSet.ChangeSetBuilder builder = ChangeSet.builder();

        if (map.get("kind") != null) {
            try {
                builder.kind(com.twindigital.simulation.model.SimulationModels.ChangeKind.valueOf(
                        map.get("kind").toString().toUpperCase()));
            } catch (Exception e) {
                builder.kind(com.twindigital.simulation.model.SimulationModels.ChangeKind.RESOURCE_CHANGE);
            }
        }
        if (map.get("target") != null) builder.target(map.get("target").toString());
        if (map.get("namespace") != null) builder.namespace(map.get("namespace").toString());
        if (map.get("cpuRequest") != null) builder.cpuRequest(map.get("cpuRequest").toString());
        if (map.get("memRequest") != null) builder.memRequest(map.get("memRequest").toString());
        if (map.get("cpuLimit") != null) builder.cpuLimit(map.get("cpuLimit").toString());
        if (map.get("memLimit") != null) builder.memLimit(map.get("memLimit").toString());
        if (map.get("replicas") != null) {
            if (map.get("replicas") instanceof Number n) {
                builder.replicas(n.intValue());
            } else {
                builder.replicas(Integer.parseInt(map.get("replicas").toString()));
            }
        }
        if (map.get("dryRunKubeApi") != null) {
            builder.dryRunKubeApi(Boolean.parseBoolean(map.get("dryRunKubeApi").toString()));
        }

        return builder.build();
    }
}
