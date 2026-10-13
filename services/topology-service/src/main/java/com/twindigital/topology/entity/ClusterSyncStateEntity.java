package com.twindigital.topology.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "td_sync_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterSyncStateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cluster_name", nullable = false, unique = true)
    private String clusterName;

    @Column(name = "status")
    @Builder.Default
    private String status = "HEALTHY";

    @Column(name = "connected")
    @Builder.Default
    private boolean connected = true;

    @Column(name = "lag_seconds")
    @Builder.Default
    private long lagSeconds = 0;

    @Column(name = "reconnect_count")
    @Builder.Default
    private int reconnectCount = 0;

    @Column(name = "last_event_at")
    @Builder.Default
    private Instant lastEventAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
