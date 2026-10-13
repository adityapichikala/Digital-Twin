package com.twindigital.costrisk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class PrometheusClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public PrometheusClient(
            @Value("${services.prometheus.url:http://localhost:9090}") String prometheusUrl,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl(prometheusUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Queries Prometheus for per-pod CPU usage in millicores (7-day p95 or instantaneous rate).
     * Returns Map: podName -> p95 CPU millicores.
     */
    public Map<String, Double> queryPodCpuUsageP95() {
        String query = "quantile_over_time(0.95, rate(container_cpu_usage_seconds_total{container!=\"\",container!=\"POD\"}[5m])[7d])";
        Map<String, Double> result = executeVectorQuery(query);
        if (result.isEmpty()) {
            // Fallback query without 7-day range if Prometheus has shorter retention
            String fallbackQuery = "rate(container_cpu_usage_seconds_total{container!=\"\",container!=\"POD\"}[5m])";
            result = executeVectorQuery(fallbackQuery);
        }

        // Convert CPU cores (e.g. 0.045) to millicores (45.0m)
        Map<String, Double> millicoresMap = new HashMap<>();
        for (Map.Entry<String, Double> e : result.entrySet()) {
            millicoresMap.put(e.getKey(), e.getValue() * 1000.0);
        }
        return millicoresMap;
    }

    /**
     * Queries Prometheus for per-pod Memory working set usage in bytes (7-day p95).
     * Returns Map: podName -> p95 Memory bytes.
     */
    public Map<String, Long> queryPodMemoryUsageP95() {
        String query = "quantile_over_time(0.95, container_memory_working_set_bytes{container!=\"\",container!=\"POD\"}[7d])";
        Map<String, Double> result = executeVectorQuery(query);
        if (result.isEmpty()) {
            String fallbackQuery = "container_memory_working_set_bytes{container!=\"\",container!=\"POD\"}";
            result = executeVectorQuery(fallbackQuery);
        }

        Map<String, Long> bytesMap = new HashMap<>();
        for (Map.Entry<String, Double> e : result.entrySet()) {
            bytesMap.put(e.getKey(), e.getValue().longValue());
        }
        return bytesMap;
    }

    private Map<String, Double> executeVectorQuery(String promQl) {
        Map<String, Double> metricMap = new HashMap<>();
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/query")
                            .queryParam("query", promQl)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(4))
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                if ("success".equalsIgnoreCase(root.path("status").asText())) {
                    JsonNode results = root.path("data").path("result");
                    if (results.isArray()) {
                        for (JsonNode item : results) {
                            String pod = item.path("metric").path("pod").asText();
                            if (pod == null || pod.isBlank()) {
                                pod = item.path("metric").path("pod_name").asText();
                            }
                            JsonNode valueNode = item.path("value");
                            if (!pod.isBlank() && valueNode.isArray() && valueNode.size() >= 2) {
                                double val = Double.parseDouble(valueNode.get(1).asText());
                                metricMap.put(pod, val);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Prometheus query '{}' unavailable: {}", promQl, e.getMessage());
        }
        return metricMap;
    }
}
