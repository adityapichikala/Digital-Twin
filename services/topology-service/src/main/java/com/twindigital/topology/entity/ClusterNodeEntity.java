package com.twindigital.topology.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "td_nodes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterNodeEntity {
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

    @Column(name = "ready")
    @Builder.Default
    private boolean ready = true;

    @Column(name = "status")
    @Builder.Default
    private String status = "Ready";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    @Column(name = "last_synced_at")
    @Builder.Default
    private Instant lastSyncedAt = Instant.now();
}
