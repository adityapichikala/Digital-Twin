package com.twindigital.costrisk.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

public class CostRiskEntities {

    @Entity
    @Table(name = "td_instance_pricing")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstancePricingEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private UUID id;

        @Column(name = "region", nullable = false)
        private String region;

        @Column(name = "instance_type", nullable = false)
        private String instanceType;

        @Column(name = "on_demand_price", nullable = false)
        private double onDemandPrice;

        @Column(name = "spot_price")
        private Double spotPrice;

        @Column(name = "vcpu")
        private Integer vcpu;

        @Column(name = "memory_gib")
        private Double memoryGib;

        @Column(name = "updated_at", nullable = false)
        @Builder.Default
        private Instant updatedAt = Instant.now();
    }

    @Entity
    @Table(name = "td_cost_assessments")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostAssessmentEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private UUID id;

        @Column(name = "simulation_id", nullable = false, unique = true)
        private UUID simulationId;

        @Column(name = "cluster_name", nullable = false)
        private String clusterName;

        @Column(name = "baseline_cost_usd")
        private Double baselineCostUsd;

        @Column(name = "proposed_cost_usd")
        private Double proposedCostUsd;

        @Column(name = "delta_usd")
        private Double deltaUsd;

        @Column(name = "risk_score")
        private Integer riskScore;

        @Column(name = "risk_level")
        private String riskLevel;

        @Column(name = "explanations", columnDefinition = "jsonb")
        @JdbcTypeCode(SqlTypes.JSON)
        private String explanations;

        @Column(name = "computed_at", nullable = false)
        @Builder.Default
        private Instant computedAt = Instant.now();
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
}
