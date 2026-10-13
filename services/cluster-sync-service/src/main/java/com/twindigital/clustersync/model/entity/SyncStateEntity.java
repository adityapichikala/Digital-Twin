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
@Table(name = "td_sync_state", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sync_state_cluster_name", columnNames = {"cluster_name"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cluster_name", nullable = false, length = 200)
    private String clusterName;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "HEALTHY";

    @Column(name = "connected", nullable = false)
    @Builder.Default
    private boolean connected = true;

    @Column(name = "lag_seconds", nullable = false)
    @Builder.Default
    private long lagSeconds = 0;

    @Column(name = "reconnect_count", nullable = false)
    @Builder.Default
    private int reconnectCount = 0;

    @Column(name = "last_event_at", nullable = false)
    @Builder.Default
    private Instant lastEventAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_counts", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> resourceCounts = new HashMap<>();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
