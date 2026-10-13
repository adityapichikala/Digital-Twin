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
@Table(name = "td_nodes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nodes_cluster_name", columnNames = {"cluster_name", "name"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cluster_name", nullable = false, length = 200)
    private String clusterName;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "allocatable_cpu", length = 50)
    private String allocatableCpu;

    @Column(name = "allocatable_memory", length = 50)
    private String allocatableMemory;

    @Column(name = "capacity_cpu", length = 50)
    private String capacityCpu;

    @Column(name = "capacity_memory", length = 50)
    private String capacityMemory;

    @Column(name = "ready", nullable = false)
    @Builder.Default
    private boolean ready = true;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "Ready";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> conditions = new HashMap<>();

    @Column(name = "last_synced_at", nullable = false)
    @Builder.Default
    private Instant lastSyncedAt = Instant.now();
}
