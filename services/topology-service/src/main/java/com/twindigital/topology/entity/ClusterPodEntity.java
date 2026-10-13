package com.twindigital.topology.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "td_pods")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterPodEntity {
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

    @Column(name = "restarts")
    @Builder.Default
    private int restarts = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    @Column(name = "last_synced_at")
    @Builder.Default
    private Instant lastSyncedAt = Instant.now();
}
