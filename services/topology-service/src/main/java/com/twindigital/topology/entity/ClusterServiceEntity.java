package com.twindigital.topology.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "td_services")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterServiceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cluster_name", nullable = false)
    private String clusterName;

    @Column(name = "namespace", nullable = false)
    private String namespace;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "service_type")
    @Builder.Default
    private String serviceType = "ClusterIP";

    @Column(name = "cluster_ip")
    private String clusterIp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selector", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> selector = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ports", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> ports = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "labels", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    @Column(name = "last_synced_at")
    @Builder.Default
    private Instant lastSyncedAt = Instant.now();
}
