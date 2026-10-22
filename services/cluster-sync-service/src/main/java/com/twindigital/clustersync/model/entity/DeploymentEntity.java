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
@Table(name = "td_deployments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_deployments_cluster_ns_name", columnNames = {"cluster_name", "namespace", "name"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cluster_name", nullable = false, length = 200)
    private String clusterName;

    @Column(name = "namespace", nullable = false, length = 200)
    private String namespace;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "replicas", nullable = false)
    @Builder.Default
    private int replicas = 0;

    @Column(name = "ready_replicas", nullable = false)
    @Builder.Default
    private int readyReplicas = 0;

    @Column(name = "available_replicas", nullable = false)
    @Builder.Default
    private int availableReplicas = 0;

    @Column(name = "updated_replicas", nullable = false)
    @Builder.Default
    private int updatedReplicas = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selector", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> selector = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "annotations", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> annotations = new HashMap<>();

    @Column(name = "last_synced_at", nullable = false)
    @Builder.Default
    private Instant lastSyncedAt = Instant.now();
}
