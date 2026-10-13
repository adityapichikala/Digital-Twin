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
@Table(name = "td_pdbs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pdbs_cluster_ns_name", columnNames = {"cluster_name", "namespace", "name"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdbEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cluster_name", nullable = false, length = 200)
    private String clusterName;

    @Column(name = "namespace", nullable = false, length = 200)
    private String namespace;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "min_available", length = 50)
    private String minAvailable;

    @Column(name = "max_unavailable", length = 50)
    private String maxUnavailable;

    @Column(name = "current_healthy")
    private Integer currentHealthy;

    @Column(name = "desired_healthy")
    private Integer desiredHealthy;

    @Column(name = "disruptions_allowed")
    private Integer disruptionsAllowed;

    @Column(name = "expected_pods")
    private Integer expectedPods;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selector", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> selector = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    @Column(name = "last_synced_at", nullable = false)
    @Builder.Default
    private Instant lastSyncedAt = Instant.now();
}
