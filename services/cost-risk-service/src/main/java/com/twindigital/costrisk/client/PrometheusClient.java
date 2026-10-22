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

    public static final String CPU_QUERY =
            "quantile_over_time(0.95, rate(container_cpu_usage_seconds_total{container!=\"\",container!=\"POD\"}[5m])[7d:5m])";
    public static final String CPU_FALLBACK_QUERY =
            "rate(container_cpu_usage_seconds_total{container!=\"\",container!=\"POD\"}[5m])";
    public static final String MEMORY_QUERY =
            "quantile_over_time(0.95, container_memory_working_set_bytes{container!=\"\",container!=\"POD\"}[7d])";
    public static final String MEMORY_FALLBACK_QUERY =
            "container_memory_working_set_bytes{container!=\"\",container!=\"POD\"}";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public PrometheusClient(
            @Value("${services.prometheus.url:http://localhost:9090}") String prometheusUrl,
            ObjectMapper objectMapper) {
        this(WebClient.builder().baseUrl(prometheusUrl).build(), objectMapper);
    }

    public PrometheusClient(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Queries Prometheus for per-pod CPU usage in millicores (7-day p95 or instantaneous rate).
     * Returns Map: podName -> p95 CPU millicores.
     */
    public Map<String, Double> queryPodCpuUsageP95() {
        Map<String, Double> result = executeVectorQuery(CPU_QUERY);
        if (result.isEmpty()) {
            // Fallback query without 7-day range if Prometheus has shorter retention
            result = executeVectorQuery(CPU_FALLBACK_QUERY);
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
        Map<String, Double> result = executeVectorQuery(MEMORY_QUERY);
        if (result.isEmpty()) {
            result = executeVectorQuery(MEMORY_FALLBACK_QUERY);
        }

        Map<String, Long> bytesMap = new HashMap<>();
        for (Map.Entry<String, Double> e : result.entrySet()) {
            bytesMap.put(e.getKey(), e.getValue().longValue());
        }
        return bytesMap;
    }

    private Map<String, Double> executeVectorQuery(String promQl) {
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

            return parsePrometheusResponse(response);
        } catch (Exception e) {
            log.debug("Prometheus query '{}' unavailable: {}", promQl, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Parses Prometheus vector JSON response and aggregates values per pod across containers.
     */
    public Map<String, Double> parsePrometheusResponse(String response) {
        Map<String, Double> metricMap = new HashMap<>();
        if (response == null || response.isBlank()) {
            return metricMap;
        }
        try {
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
                            try {
                                double val = Double.parseDouble(valueNode.get(1).asText());
                                if (!Double.isNaN(val) && !Double.isInfinite(val)) {
                                    // Sum per pod across containers
                                    metricMap.merge(pod, val, Double::sum);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse Prometheus response: {}", e.getMessage());
        }
        return metricMap;
    }
}
