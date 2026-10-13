package com.twindigital.clustersync.controller;

import com.twindigital.clustersync.config.ClusterSyncProperties;
import com.twindigital.clustersync.model.dto.SyncStatusResponse;
import com.twindigital.clustersync.model.entity.*;
import com.twindigital.clustersync.repository.*;
import com.twindigital.clustersync.service.SyncHealthTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SyncController {

    private final SyncHealthTracker healthTracker;
    private final ClusterSyncProperties properties;
    private final NodeRepository nodeRepository;
    private final PodRepository podRepository;
    private final ServiceRepository serviceRepository;
    private final DeploymentRepository deploymentRepository;
    private final HpaRepository hpaRepository;
    private final PdbRepository pdbRepository;

    /**
     * Expose sync status (HEALTHY or STALE, lag in seconds, connection state).
     * Mapped to both /api/sync/status and /api/v1/sync/status for compatibility with Gateway proxying.
     */
    @GetMapping({"/api/sync/status", "/api/v1/sync/status"})
    public ResponseEntity<SyncStatusResponse> status() {
        SyncStatusResponse response = healthTracker.buildStatusResponse();
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve normalized nodes snapshot.
     */
    @GetMapping({"/api/sync/nodes", "/api/v1/sync/nodes"})
    public ResponseEntity<List<NodeEntity>> getNodes(
            @RequestParam(required = false) String cluster) {
        String clusterName = cluster != null ? cluster : properties.getClusterName();
        return ResponseEntity.ok(nodeRepository.findByClusterName(clusterName));
    }

    /**
     * Retrieve normalized pods snapshot.
     */
    @GetMapping({"/api/sync/pods", "/api/v1/sync/pods"})
    public ResponseEntity<List<PodEntity>> getPods(
            @RequestParam(required = false) String cluster,
            @RequestParam(required = false) String namespace) {
        String clusterName = cluster != null ? cluster : properties.getClusterName();
        if (namespace != null && !namespace.isBlank()) {
            return ResponseEntity.ok(podRepository.findByClusterNameAndNamespace(clusterName, namespace));
        }
        return ResponseEntity.ok(podRepository.findByClusterName(clusterName));
    }

    /**
     * Retrieve normalized services snapshot.
     */
    @GetMapping({"/api/sync/services", "/api/v1/sync/services"})
    public ResponseEntity<List<ServiceEntity>> getServices(
            @RequestParam(required = false) String cluster) {
        String clusterName = cluster != null ? cluster : properties.getClusterName();
        return ResponseEntity.ok(serviceRepository.findByClusterName(clusterName));
    }

    /**
     * Retrieve normalized deployments snapshot.
     */
    @GetMapping({"/api/sync/deployments", "/api/v1/sync/deployments"})
    public ResponseEntity<List<DeploymentEntity>> getDeployments(
            @RequestParam(required = false) String cluster) {
        String clusterName = cluster != null ? cluster : properties.getClusterName();
        return ResponseEntity.ok(deploymentRepository.findByClusterName(clusterName));
    }

    /**
     * Retrieve normalized HPAs snapshot.
     */
    @GetMapping({"/api/sync/hpas", "/api/v1/sync/hpas"})
    public ResponseEntity<List<HpaEntity>> getHpas(
            @RequestParam(required = false) String cluster) {
        String clusterName = cluster != null ? cluster : properties.getClusterName();
        return ResponseEntity.ok(hpaRepository.findByClusterName(clusterName));
    }

    /**
     * Retrieve normalized PDBs snapshot.
     */
    @GetMapping({"/api/sync/pdbs", "/api/v1/sync/pdbs"})
    public ResponseEntity<List<PdbEntity>> getPdbs(
            @RequestParam(required = false) String cluster) {
        String clusterName = cluster != null ? cluster : properties.getClusterName();
        return ResponseEntity.ok(pdbRepository.findByClusterName(clusterName));
    }
}
