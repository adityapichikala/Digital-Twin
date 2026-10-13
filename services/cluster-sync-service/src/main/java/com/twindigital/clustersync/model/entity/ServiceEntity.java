package com.twindigital.clustersync.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "td_services", uniqueConstraints = {
        @UniqueConstraint(name = "uk_services_cluster_ns_name", columnNames = {"cluster_name", "namespace", "name"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cluster_name", nullable = false, length = 200)
    private String clusterName;

    @Column(name = "namespace", nullable = false, length = 200)
    private String namespace;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "service_type", length = 50)
    @Builder.Default
    private String serviceType = "ClusterIP";

    @Column(name = "cluster_ip", length = 50)
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

    @Column(name = "last_synced_at", nullable = false)
    @Builder.Default
    private Instant lastSyncedAt = Instant.now();
}
