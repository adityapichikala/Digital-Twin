package com.twindigital.costrisk.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PrometheusClientTest {

    private PrometheusClient prometheusClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        WebClient mockWebClient = mock(WebClient.class);
        prometheusClient = new PrometheusClient(mockWebClient, objectMapper);
    }

    @Test
    @DisplayName("Should use valid 7d:5m subquery syntax for quantile_over_time")
    void shouldUseValidPromQlSubquerySyntax() {
        assertThat(PrometheusClient.CPU_QUERY)
                .isEqualTo("quantile_over_time(0.95, rate(container_cpu_usage_seconds_total{container!=\"\",container!=\"POD\"}[5m])[7d:5m])");
    }

    @Test
    @DisplayName("Should sum metric values per pod across multiple containers")
    void shouldSumAcrossMultipleContainersPerPod() {
        // Given Prometheus vector response with 2 containers for billing-service-pod-1 and 1 container for auth-pod
        String json = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [
                      {
                        "metric": {
                          "pod": "billing-service-pod-1",
                          "container": "billing-app"
                        },
                        "value": [1700000000, "0.035"]
                      },
                      {
                        "metric": {
                          "pod": "billing-service-pod-1",
                          "container": "istio-proxy"
                        },
                        "value": [1700000000, "0.015"]
                      },
                      {
                        "metric": {
                          "pod_name": "auth-service-pod-1",
                          "container": "auth-app"
                        },
                        "value": [1700000000, "0.040"]
                      }
                    ]
                  }
                }
                """;

        // When
        Map<String, Double> parsed = prometheusClient.parsePrometheusResponse(json);

        // Then
        // billing-service-pod-1 should sum: 0.035 + 0.015 = 0.050
        assertThat(parsed).containsEntry("billing-service-pod-1", 0.050);
        // auth-service-pod-1 has 1 container: 0.040
        assertThat(parsed).containsEntry("auth-service-pod-1", 0.040);
        assertThat(parsed).hasSize(2);
    }

    @Test
    @DisplayName("Should return empty map on invalid or empty Prometheus response")
    void shouldHandleEmptyOrInvalidResponse() {
        assertThat(prometheusClient.parsePrometheusResponse(null)).isEmpty();
        assertThat(prometheusClient.parsePrometheusResponse("")).isEmpty();
        assertThat(prometheusClient.parsePrometheusResponse("{\"status\":\"error\"}")).isEmpty();
    }
}
