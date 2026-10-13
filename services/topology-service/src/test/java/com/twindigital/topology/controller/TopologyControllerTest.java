package com.twindigital.topology.controller;

import com.twindigital.topology.model.TopologyDTOs.*;
import com.twindigital.topology.service.TopologyGraphService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TopologyController.class)
class TopologyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TopologyGraphService topologyGraphService;

    @Test
    @DisplayName("GET /api/topology should return nodes, edges, health, staleness")
    void shouldReturnTopologyGraph() throws Exception {
        TopologyNode node = TopologyNode.builder()
                .id("service:twindigital/auth-service")
                .name("auth-service")
                .type("SERVICE")
                .health("HEALTHY")
                .healthColor("#10b981")
                .build();

        TopologyEdge edge = TopologyEdge.builder()
                .id("e1")
                .source("service:twindigital/gateway")
                .target("service:twindigital/auth-service")
                .type("INGRESS")
                .build();

        ClusterHealthSummary health = ClusterHealthSummary.builder()
                .healthyCount(5)
                .degradedCount(0)
                .criticalCount(0)
                .totalCount(5)
                .overallStatus("HEALTHY")
                .build();

        StalenessInfo staleness = StalenessInfo.builder()
                .isStale(false)
                .lagSeconds(3)
                .lastSyncedAt("2026-10-14T00:00:00Z")
                .syncStatus("HEALTHY")
                .build();

        TopologyResponse response = TopologyResponse.builder()
                .nodes(List.of(node))
                .edges(List.of(edge))
                .health(health)
                .staleness(staleness)
                .build();

        when(topologyGraphService.getTopology(any())).thenReturn(response);

        mockMvc.perform(get("/api/topology")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[0].name").value("auth-service"))
                .andExpect(jsonPath("$.edges[0].id").value("e1"))
                .andExpect(jsonPath("$.health.overallStatus").value("HEALTHY"))
                .andExpect(jsonPath("$.staleness.stale").value(false));
    }

    @Test
    @DisplayName("GET /api/topology/blast-radius with target should return blast radius response")
    void shouldReturnBlastRadius() throws Exception {
        TopologyNode target = TopologyNode.builder()
                .id("service:twindigital/billing-service")
                .name("billing-service")
                .type("SERVICE")
                .health("HEALTHY")
                .build();

        TopologyNode upstream = TopologyNode.builder()
                .id("service:twindigital/gateway")
                .name("gateway")
                .type("SERVICE")
                .health("HEALTHY")
                .build();

        BlastRadiusResponse blastResponse = BlastRadiusResponse.builder()
                .target(target)
                .upstream(List.of(upstream))
                .downstream(Collections.emptyList())
                .impactedNodeIds(Set.of("service:twindigital/billing-service", "service:twindigital/gateway"))
                .impactedEdgeIds(Set.of("e:gw-billing"))
                .totalImpacted(1)
                .riskScore("MEDIUM")
                .explanation("Target billing-service has 1 upstream caller")
                .build();

        when(topologyGraphService.calculateBlastRadius(any(), eq("billing-service"))).thenReturn(blastResponse);

        mockMvc.perform(get("/api/topology/blast-radius")
                        .param("target", "billing-service")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.name").value("billing-service"))
                .andExpect(jsonPath("$.upstream[0].name").value("gateway"))
                .andExpect(jsonPath("$.riskScore").value("MEDIUM"))
                .andExpect(jsonPath("$.totalImpacted").value(1));
    }

    @Test
    @DisplayName("GET /api/topology/blast-radius without target should return 400 Bad Request")
    void shouldReturnBadRequestWithoutTarget() throws Exception {
        mockMvc.perform(get("/api/topology/blast-radius")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
