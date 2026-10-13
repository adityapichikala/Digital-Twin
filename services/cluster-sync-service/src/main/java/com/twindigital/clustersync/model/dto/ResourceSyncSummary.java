package com.twindigital.clustersync.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceSyncSummary {
    private boolean synced;
    private long count;
    private Instant lastEventAt;
}
