package com.twindigital.topology.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

public class TopologyDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopologyNode {
        private String id;
        private String name;
        private String type; // "NODE", "POD", "DEPLOYMENT", "SERVICE", "INGRESS"
        private String namespace;
        private String health; // "HEALTHY", "DEGRADED", "CRITICAL"
        private String healthColor; // "#10b981", "#f59e0b", "#ef4444"
        @Builder.Default
        private Map<String, Object> details = new HashMap<>();
        @Builder.Default
        private Map<String, String> labels = new HashMap<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopologyEdge {
        private String id;
        private String source;
        private String target;
        private String type; // "HIERARCHY", "DEPENDENCY", "INGRESS"
        private String label;
        private boolean animated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterHealthSummary {
        private int healthyCount;
        private int degradedCount;
        private int criticalCount;
        private int totalCount;
        private String overallStatus; // "HEALTHY", "DEGRADED", "CRITICAL"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StalenessInfo {
        private boolean isStale;
        private long lagSeconds;
        private String lastSyncedAt;
        private String syncStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopologyResponse {
        private List<TopologyNode> nodes;
        private List<TopologyEdge> edges;
        private ClusterHealthSummary health;
        private StalenessInfo staleness;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlastRadiusResponse {
        private TopologyNode target;
        private List<TopologyNode> upstream;
        private List<TopologyNode> downstream;
        private Set<String> impactedNodeIds;
        private Set<String> impactedEdgeIds;
        private int totalImpacted;
        private String riskScore; // "LOW", "MEDIUM", "HIGH", "CRITICAL"
        private String explanation;
    }
}
