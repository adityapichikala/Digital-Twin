package com.twindigital.topology.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "td_deployments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterDeploymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cluster_name", nullable = false)
    private String clusterName;

    @Column(name = "namespace", nullable = false)
    private String namespace;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "replicas")
    @Builder.Default
    private int replicas = 0;

    @Column(name = "ready_replicas")
    @Builder.Default
    private int readyReplicas = 0;

    @Column(name = "available_replicas")
    @Builder.Default
    private int availableReplicas = 0;

    @Column(name = "updated_replicas")
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

    @Column(name = "last_synced_at")
    @Builder.Default
    private Instant lastSyncedAt = Instant.now();
}
