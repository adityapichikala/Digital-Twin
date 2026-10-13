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
@Table(name = "td_hpas", uniqueConstraints = {
        @UniqueConstraint(name = "uk_hpas_cluster_ns_name", columnNames = {"cluster_name", "namespace", "name"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cluster_name", nullable = false, length = 200)
    private String clusterName;

    @Column(name = "namespace", nullable = false, length = 200)
    private String namespace;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "target_kind", length = 100)
    private String targetKind;

    @Column(name = "target_name", length = 200)
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

    @Column(name = "current_cpu_utilization")
    private Integer currentCpuUtilization;

    @Column(name = "target_cpu_utilization")
    private Integer targetCpuUtilization;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    @Column(name = "last_synced_at", nullable = false)
    @Builder.Default
    private Instant lastSyncedAt = Instant.now();
}
