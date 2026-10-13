package com.twindigital.clustersync.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncStatusResponse {
    /**
     * Overall sync status: "HEALTHY" or "STALE"
     */
    private String status;

    /**
     * Sync lag in seconds since last event or heartbeat
     */
    private long lagSeconds;

    /**
     * Mirror cluster name
     */
    private String clusterName;

    /**
     * Watch connection state
     */
    private boolean connected;

    /**
     * Last event timestamp
     */
    private Instant lastEventTimestamp;

    /**
     * Reconnect count
     */
    private int reconnectAttempts;

    /**
     * Current backoff delay in seconds
     */
    private long currentBackoffDelaySeconds;

    /**
     * Per-resource sync details (nodes, pods, services, deployments, hpas, pdbs)
     */
    private Map<String, ResourceSyncSummary> resourceSyncStatus;

    /**
     * Additional message or error description
     */
    private String message;
}
