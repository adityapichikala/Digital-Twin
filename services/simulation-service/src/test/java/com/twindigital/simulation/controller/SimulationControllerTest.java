package com.twindigital.simulation.controller;

import com.twindigital.simulation.entity.SimulationEntities.SimulationAuditEntity;
import com.twindigital.simulation.model.SimulationModels.*;
import com.twindigital.simulation.repository.SimulationRepositories.SimulationAuditRepository;
import com.twindigital.simulation.service.SimulationEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SimulationController.class)
class SimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SimulationEngine simulationEngine;

    @MockBean
    private SimulationAuditRepository auditRepository;

    @Test
    @DisplayName("POST /api/v1/simulations should process change set and return simulation result")
    void shouldRunSimulation() throws Exception {
        UUID simId = UUID.randomUUID();
        SimulationResult result = SimulationResult.builder()
                .simulationId(simId.toString())
                .riskLevel(RiskLevel.LOW)
                .score(10)
                .predictedOutcome("Change is safe to apply")
                .findings(List.of(SimulationFinding.builder()
                        .check("SchedulingFit")
                        .severity(FindingSeverity.INFO)
                        .explanation("All pods fit")
                        .build()))
                .executedAt(Instant.now())
                .build();

        when(simulationEngine.runSimulation(any())).thenReturn(result);

        String jsonBody = """
                {
                    "clusterName": "twindigital-cluster",
                    "user": "test-admin",
                    "changeSet": {
                        "kind": "SCALE",
                        "target": "billing-service",
                        "replicas": 3,
                        "cpuRequest": "100m",
                        "memRequest": "256Mi"
                    }
                }
                """;

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "test-admin")
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value(simId.toString()))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.score").value(10))
                .andExpect(jsonPath("$.predictedOutcome").value("Change is safe to apply"));
    }

    @Test
    @DisplayName("GET /api/v1/simulations/{id} should return audit record if found")
    void shouldGetSimulationById() throws Exception {
        UUID simId = UUID.randomUUID();
        SimulationAuditEntity audit = SimulationAuditEntity.builder()
                .id(UUID.randomUUID())
                .simulationId(simId)
                .userId("admin")
                .clusterName("twindigital-cluster")
                .riskLevel("CRITICAL")
                .score(85)
                .predictedOutcome("2 pods would stay Pending")
                .findingsCount(2)
                .executedAt(Instant.now())
                .build();

        when(auditRepository.findById(simId)).thenReturn(Optional.of(audit));

        mockMvc.perform(get("/api/v1/simulations/" + simId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value(simId.toString()))
                .andExpect(jsonPath("$.riskLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.score").value(85));
    }
}
