package com.twindigital.costrisk.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class AwsCostExplorerClient {

    private final CostExplorerClient costExplorerClient;
    private final Cache<String, AwsCostData> costCache;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AwsCostData {
        private double totalMonthlyCostUsd;
        @Builder.Default
        private Map<String, Double> costByService = new HashMap<>();
        @Builder.Default
        private Map<String, Double> costByNamespace = new HashMap<>();
        private String timePeriodStart;
        private String timePeriodEnd;
    }

    public AwsCostExplorerClient(
            @Value("${aws.region:ap-south-1}") String awsRegion,
            @Value("${aws.cost-explorer.cache-ttl-minutes:360}") long cacheTtlMinutes,
            @Autowired(required = false) CostExplorerClient customClient) {

        if (customClient != null) {
            this.costExplorerClient = customClient;
        } else {
            CostExplorerClient client = null;
            try {
                client = CostExplorerClient.builder()
                        .region(Region.of(awsRegion))
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
            } catch (Exception e) {
                log.info("AWS CostExplorer client default initialization: {}", e.getMessage());
            }
            this.costExplorerClient = client;
        }

        // Cache Cost Explorer calls with sensible TTL (billed $0.01 per call)
        this.costCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(Math.max(15, cacheTtlMinutes)))
                .maximumSize(50)
                .build();
    }

    /**
     * Retrieves cached AWS Cost Explorer billing data or fetches live if cache expired.
     */
    public Optional<AwsCostData> getMonthlyCost(String clusterName) {
        String cacheKey = clusterName != null ? clusterName : "default-cluster";
        AwsCostData cached = costCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Returning cached AWS Cost Explorer data for key '{}'", cacheKey);
            return Optional.of(cached);
        }

        if (costExplorerClient == null) {
            log.debug("AWS CostExplorerClient not configured (offline mode)");
            return Optional.empty();
        }

        try {
            LocalDate now = LocalDate.now();
            LocalDate start = now.withDayOfMonth(1);
            if (now.getDayOfMonth() == 1) {
                start = now.minusMonths(1).withDayOfMonth(1);
            }
            LocalDate end = now.plusDays(1);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateInterval interval = DateInterval.builder()
                    .start(start.format(fmt))
                    .end(end.format(fmt))
                    .build();

            // Note: AWS Cost Explorer aggregates costs account-wide unless filtered by cluster tag.
            // Filter by standard Kubernetes cluster tag kubernetes.io/cluster/<clusterName>
            GetCostAndUsageRequest.Builder requestBuilder = GetCostAndUsageRequest.builder()
                    .timePeriod(interval)
                    .granularity(Granularity.MONTHLY)
                    .metrics("UnblendedCost")
                    .groupBy(GroupDefinition.builder()
                            .type(GroupDefinitionType.DIMENSION)
                            .key("SERVICE")
                            .build());

            if (clusterName != null && !clusterName.isBlank()) {
                requestBuilder.filter(Expression.builder()
                        .tags(TagValues.builder()
                                .key("kubernetes.io/cluster/" + clusterName)
                                .values("owned", "shared")
                                .build())
                        .build());
            }

            GetCostAndUsageRequest request = requestBuilder.build();

            GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);

            double total = 0.0;
            Map<String, Double> byService = new HashMap<>();

            for (ResultByTime result : response.resultsByTime()) {
                for (Group group : result.groups()) {
                    String service = group.keys().isEmpty() ? "Other" : group.keys().get(0);
                    MetricValue metricVal = group.metrics().get("UnblendedCost");
                    double amount = (metricVal != null && metricVal.amount() != null)
                            ? Double.parseDouble(metricVal.amount()) : 0.0;

                    byService.put(service, byService.getOrDefault(service, 0.0) + amount);
                    total += amount;
                }
            }

            AwsCostData costData = AwsCostData.builder()
                    .totalMonthlyCostUsd(total)
                    .costByService(byService)
                    .timePeriodStart(interval.start())
                    .timePeriodEnd(interval.end())
                    .build();

            costCache.put(cacheKey, costData);
            log.info("Fetched live AWS Cost Explorer data: total=${}", total);
            return Optional.of(costData);

        } catch (Exception e) {
            log.warn("AWS Cost Explorer query failed ({}). Gracefully falling back to pricing catalog.", e.getMessage());
            return Optional.empty();
        }
    }

    public void clearCache() {
        costCache.invalidateAll();
    }
}
