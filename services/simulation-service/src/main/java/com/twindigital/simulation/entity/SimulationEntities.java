package com.twindigital.simulation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

public class SimulationEntities {

    @Entity
    @Table(name = "simulation_audit")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationAuditEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private UUID id;

        @Column(name = "simulation_id", nullable = false)
        private UUID simulationId;

        @Column(name = "user_id", nullable = false)
        private String userId;

        @Column(name = "cluster_name", nullable = false)
        private String clusterName;

        @Column(name = "change_set", columnDefinition = "jsonb")
        @JdbcTypeCode(SqlTypes.JSON)
        private String changeSet;

        @Column(name = "result", columnDefinition = "jsonb")
        @JdbcTypeCode(SqlTypes.JSON)
        private String result;

        @Column(name = "risk_level", nullable = false)
        private String riskLevel;

        @Column(name = "score", nullable = false)
        private int score;

        @Column(name = "predicted_outcome", columnDefinition = "text")
        private String predictedOutcome;

        @Column(name = "findings_count", nullable = false)
        private int findingsCount;

        @Column(name = "executed_at", nullable = false)
        @Builder.Default
        private Instant executedAt = Instant.now();
    }

    @Entity
    @Table(name = "td_nodes")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterNodeEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private UUID id;

        @Column(name = "cluster_name", nullable = false)
        private String clusterName;

        @Column(name = "name", nullable = false)
        private String name;

        @Column(name = "allocatable_cpu")
        private String allocatableCpu;

        @Column(name = "allocatable_memory")
        private String allocatableMemory;

        @Column(name = "capacity_cpu")
        private String capacityCpu;

        @Column(name = "capacity_memory")
        private String capacityMemory;

        @Column(name = "ready", nullable = false)
        @Builder.Default
        private boolean ready = true;

        @Column(name = "status", nullable = false)
        @Builder.Default
        private String status = "Ready";

        @Column(name = "labels", columnDefinition = "jsonb")
        @JdbcTypeCode(SqlTypes.JSON)
        @Builder.Default
        private Map<String, String> labels = new HashMap<>();
    }

    @Entity
    @Table(name = "td_pods")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterPodEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private UUID id;

        @Column(name = "cluster_name", nullable = false)
        private String clusterName;

        @Column(name = "namespace", nullable = false)
        private String namespace;

        @Column(name = "name", nullable = false)
        private String name;

        @Column(name = "node_name")
        private String nodeName;

        @Column(name = "owner_kind")
        private String ownerKind;

        @Column(name = "owner_name")
        private String ownerName;

        @Column(name = "cpu_requests")
        private String cpuRequests;

        @Column(name = "memory_requests")
        private String memoryRequests;

        @Column(name = "cpu_limits")
        private String cpuLimits;

        @Column(name = "memory_limits")
        private String memoryLimits;

        @Column(name = "phase")
        @Builder.Default
        private String phase = "Running";

        @Column(name = "restarts", nullable = false)
        @Builder.Default
        private int restarts = 0;

        @Column(name = "labels", columnDefinition = "jsonb")
        @JdbcTypeCode(SqlTypes.JSON)
        @Builder.Default
        private Map<String, String> labels = new HashMap<>();
    }

    @Entity
    @Table(name = "td_deployments")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterDeploymentEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private UUID id;

        @Column(name = "cluster_name", nullable = false)
        private String clusterName;

        @Column(name = "namespace", nullable = false)
        private String namespace;

        @Column(name = "name", nullable = false)
        private String name;

        @Column(name = "replicas", nullable = false)
        @Builder.Default
        private int replicas = 1;

        @Column(name = "ready_replicas", nullable = false)
        @Builder.Default
        private int readyReplicas = 1;

        @Column(name = "available_replicas", nullable = false)
        @Builder.Default
        private int availableReplicas = 1;

        @Column(name = "selector", columnDefinition = "jsonb")
        @JdbcTypeCode(SqlTypes.JSON)
        @Builder.Default
        private Map<String, String> selector = new HashMap<>();

        @Column(name = "labels", columnDefinition = "jsonb")
        @JdbcTypeCode(SqlTypes.JSON)
        @Builder.Default
        private Map<String, String> labels = new HashMap<>();
    }

    @Entity
    @Table(name = "td_hpas")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterHpaEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private UUID id;

        @Column(name = "cluster_name", nullable = false)
        private String clusterName;

        @Column(name = "namespace", nullable = false)
        private String namespace;

        @Column(name = "name", nullable = false)
        private String name;

        @Column(name = "target_kind")
        private String targetKind;

        @Column(name = "target_name")
        private String targetName;

        @Column(name = "min_replicas")
        private Integer minReplicas;

        @Column(name = "max_replicas", nullable = false)
        @Builder.Default
        private int maxReplicas = 1;

        @Column(name = "current_replicas")
        private Integer currentReplicas;

        @Column(name = "desired_replicas")
        private Integer desiredReplicas;
    }

    @Entity
    @Table(name = "td_pdbs")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterPdbEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private UUID id;

        @Column(name = "cluster_name", nullable = false)
        private String clusterName;

        @Column(name = "namespace", nullable = false)
        private String namespace;

        @Column(name = "name", nullable = false)
        private String name;

        @Column(name = "min_available")
        private String minAvailable;

        @Column(name = "max_unavailable")
        private String maxUnavailable;

        @Column(name = "current_healthy")
        private Integer currentHealthy;

        @Column(name = "desired_healthy")
        private Integer desiredHealthy;

        @Column(name = "disruptions_allowed")
        private Integer disruptionsAllowed;

        @Column(name = "selector", columnDefinition = "jsonb")
        @JdbcTypeCode(SqlTypes.JSON)
        @Builder.Default
        private Map<String, String> selector = new HashMap<>();
    }

    @Entity
    @Table(name = "td_services")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterServiceEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private UUID id;

        @Column(name = "cluster_name", nullable = false)
        private String clusterName;

        @Column(name = "namespace", nullable = false)
        private String namespace;

        @Column(name = "name", nullable = false)
        private String name;

        @Column(name = "service_type")
        private String serviceType;

        @Column(name = "cluster_ip")
        private String clusterIp;

        @Column(name = "selector", columnDefinition = "jsonb")
        @JdbcTypeCode(SqlTypes.JSON)
        @Builder.Default
        private Map<String, String> selector = new HashMap<>();
    }
}
