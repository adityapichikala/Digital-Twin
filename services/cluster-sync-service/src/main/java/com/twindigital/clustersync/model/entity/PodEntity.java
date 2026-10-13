package com.twindigital.clustersync.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "td_pods", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pods_cluster_ns_name", columnNames = {"cluster_name", "namespace", "name"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cluster_name", nullable = false, length = 200)
    private String clusterName;

    @Column(name = "namespace", nullable = false, length = 200)
    private String namespace;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "node_name", length = 200)
    private String nodeName;

    @Column(name = "owner_kind", length = 100)
    private String ownerKind;

    @Column(name = "owner_name", length = 200)
    private String ownerName;

    @Column(name = "cpu_requests", length = 50)
    private String cpuRequests;

    @Column(name = "memory_requests", length = 50)
    private String memoryRequests;

    @Column(name = "cpu_limits", length = 50)
    private String cpuLimits;

    @Column(name = "memory_limits", length = 50)
    private String memoryLimits;

    @Column(name = "phase", length = 50)
    @Builder.Default
    private String phase = "Running";

    @Column(name = "restarts", nullable = false)
    @Builder.Default
    private int restarts = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    @Column(name = "last_synced_at", nullable = false)
    @Builder.Default
    private Instant lastSyncedAt = Instant.now();
}
