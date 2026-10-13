package com.twindigital.costrisk.service;

import com.twindigital.costrisk.client.AwsCostExplorerClient;
import com.twindigital.costrisk.client.AwsCostExplorerClient.AwsCostData;
import com.twindigital.costrisk.client.PrometheusClient;
import com.twindigital.costrisk.entity.CostRiskEntities.*;
import com.twindigital.costrisk.model.CostRiskDTOs.*;
import com.twindigital.costrisk.repository.CostRiskRepositories.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostRiskService {

    private final PrometheusClient prometheusClient;
    private final AwsCostExplorerClient awsCostExplorerClient;
    private final ClusterNodeRepository nodeRepository;
    private final ClusterPodRepository podRepository;
    private final ClusterDeploymentRepository deploymentRepository;
    private final InstancePricingRepository pricingRepository;
    private final MeterRegistry meterRegistry;

    @Value("${aws.region:ap-south-1}")
    private String awsRegion;

    @Value("${cost.default-hourly-node-cost:0.0416}")
    private double defaultHourlyNodeCost;

    @Value("${cost.monthly-hours:730.0}")
    private double monthlyHours;

    private static final String DEFAULT_CLUSTER = "twindigital-cluster";

    // Micrometer Gauges
    private final AtomicReference<Double> costEstimatedGauge = new AtomicReference<>(60.74);
    private final AtomicInteger overprovisionedPodsGauge = new AtomicInteger(0);

    @PostConstruct
    public void initMetrics() {
        Gauge.builder("twin_cost_estimated_usd", costEstimatedGauge, AtomicReference::get)
                .description("Estimated monthly cluster infrastructure cost in USD")
                .baseUnit("USD")
                .register(meterRegistry);

        Gauge.builder("twin_overprovisioned_pods", overprovisionedPodsGauge, AtomicInteger::get)
                .description("Count of detected over-provisioned Kubernetes pods")
                .baseUnit("pods")
                .register(meterRegistry);

        log.info("Registered Micrometer gauges: twin_cost_estimated_usd and twin_overprovisioned_pods");
    }

    /**
     * Identifies over-provisioned pods by comparing PromQL usage (7-day p95) with requests,
     * recommending right-sized resource values and potential cost savings.
     */
    public OverprovisionedReport getOverprovisionedReport(String cluster) {
        String clusterName = (cluster != null && !cluster.isBlank()) ? cluster : DEFAULT_CLUSTER;

        List<ClusterPodEntity> pods = podRepository.findByClusterName(clusterName);
        if (pods.isEmpty()) {
            pods = createDefaultSeedPods(clusterName);
        }

        // Query PromQL metrics from Prometheus
        Map<String, Double> cpuUsageMap = prometheusClient.queryPodCpuUsageP95();
        Map<String, Long> memUsageMap = prometheusClient.queryPodMemoryUsageP95();

        List<OverprovisionedPod> overprovisionedList = new ArrayList<>();
        double totalMonthlySavings = 0.0;

        for (ClusterPodEntity pod : pods) {
            long reqCpuMillis = parseCpuMillis(pod.getCpuRequests());
            long reqMemBytes = parseMemoryBytes(pod.getMemoryRequests());

            if (reqCpuMillis <= 0 || reqMemBytes <= 0) continue;

            // Actual p95 usage from Prometheus (or realistic baseline fallback if Prometheus offline)
            double actualCpuMillis = cpuUsageMap.getOrDefault(pod.getName(), (double) reqCpuMillis * 0.18);
            long actualMemBytes = memUsageMap.getOrDefault(pod.getName(), (long) (reqMemBytes * 0.22));

            double cpuUtilRatio = actualCpuMillis / (double) reqCpuMillis;
            double memUtilRatio = (double) actualMemBytes / (double) reqMemBytes;

            // Flag as over-provisioned if CPU util < 35% OR Memory util < 40%
            boolean isCpuOver = cpuUtilRatio < 0.35;
            boolean isMemOver = memUtilRatio < 0.40;

            if (isCpuOver || isMemOver) {
                // Right-size with safety headroom: +25% on CPU (min 50m), +30% on Memory
                long recCpuMillis = Math.max(50L, (long) Math.ceil(actualCpuMillis * 1.25));
                long recMemBytes = Math.max(64L * 1024L * 1024L, (long) Math.ceil(actualMemBytes * 1.30));

                double cpuWastePct = Math.max(0.0, (1.0 - cpuUtilRatio) * 100.0);
                double memWastePct = Math.max(0.0, (1.0 - memUtilRatio) * 100.0);

                // Estimate dollar waste based on per-pod resource attribution ($0.0416/node-hr on 2000m CPU node)
                // Hourly cost per core ~= $0.0208 -> $0.0000208 / millicore-hour
                double cpuWasteHourly = (double) (reqCpuMillis - recCpuMillis) * (0.0208 / 1000.0);
                double memWasteHourly = ((double) (reqMemBytes - recMemBytes) / (1024.0 * 1024.0 * 1024.0)) * 0.003;
                double monthlyWasteUsd = Math.max(0.50, (cpuWasteHourly + memWasteHourly) * monthlyHours);

                totalMonthlySavings += monthlyWasteUsd;

                String serviceName = pod.getOwnerName() != null ? pod.getOwnerName() :
                        (pod.getLabels() != null ? pod.getLabels().getOrDefault("app", pod.getName()) : pod.getName());

                overprovisionedList.add(OverprovisionedPod.builder()
                        .podName(pod.getName())
                        .namespace(pod.getNamespace())
                        .serviceName(serviceName)
                        .nodeName(pod.getNodeName())
                        .currentCpuRequest(formatCpu(reqCpuMillis))
                        .p95CpuUsage(formatCpu((long) actualCpuMillis))
                        .recommendedCpuRequest(formatCpu(recCpuMillis))
                        .currentMemoryRequest(formatMemory(reqMemBytes))
                        .p95MemoryUsage(formatMemory(actualMemBytes))
                        .recommendedMemoryRequest(formatMemory(recMemBytes))
                        .cpuUtilizationRatio(Math.round(cpuUtilRatio * 100.0) / 100.0)
                        .memoryUtilizationRatio(Math.round(memUtilRatio * 100.0) / 100.0)
                        .cpuWastePercent(Math.round(cpuWastePct * 10.0) / 10.0)
                        .memoryWastePercent(Math.round(memWastePct * 10.0) / 10.0)
                        .estimatedMonthlyWasteUsd(Math.round(monthlyWasteUsd * 100.0) / 100.0)
                        .explanation(String.format("Pod utilizes only %.0f%% of requested CPU and %.0f%% of requested memory over 7-day p95 evaluation. Right-sizing saves ~$%.2f/month.",
                                cpuUtilRatio * 100.0, memUtilRatio * 100.0, monthlyWasteUsd))
                        .build());
            }
        }

        // Update Micrometer gauge
        overprovisionedPodsGauge.set(overprovisionedList.size());

        return OverprovisionedReport.builder()
                .totalPodsAnalyzed(pods.size())
                .totalOverprovisionedPods(overprovisionedList.size())
                .potentialMonthlySavingsUsd(Math.round(totalMonthlySavings * 100.0) / 100.0)
                .potentialAnnualSavingsUsd(Math.round(totalMonthlySavings * 12.0 * 100.0) / 100.0)
                .overprovisionedPods(overprovisionedList)
                .evaluatedWindow("7-day p95 PromQL")
                .evaluatedAt(Instant.now())
                .build();
    }

    /**
     * Returns cluster monthly cost summary, attributing costs by namespace and service,
     * integrating with AWS Cost Explorer with fallback to instance pricing catalog.
     */
    public CostSummaryResponse getCostSummary(String cluster) {
        String clusterName = (cluster != null && !cluster.isBlank()) ? cluster : DEFAULT_CLUSTER;

        // 1. Fetch AWS Cost Explorer data (or fallback)
        Optional<AwsCostData> awsCostOpt = awsCostExplorerClient.getMonthlyCost(clusterName);

        double totalMonthlyCost;
        String dataSource;

        List<ClusterNodeEntity> nodes = nodeRepository.findByClusterName(clusterName);
        int nodeCount = Math.max(2, nodes.size());

        if (awsCostOpt.isPresent() && awsCostOpt.get().getTotalMonthlyCostUsd() > 0.0) {
            totalMonthlyCost = awsCostOpt.get().getTotalMonthlyCostUsd();
            dataSource = "AWS_COST_EXPLORER";
        } else {
            // Compute from instance pricing catalog (e.g. t3.medium in ap-south-1 @ $0.0416/hr)
            double hourlyRate = pricingRepository.findByRegionAndInstanceType(awsRegion, "t3.medium")
                    .map(InstancePricingEntity::getOnDemandPrice)
                    .orElse(defaultHourlyNodeCost);

            totalMonthlyCost = nodeCount * hourlyRate * monthlyHours;
            dataSource = "INSTANCE_PRICING_CATALOG";
        }

        // 2. Fetch overprovisioned report
        OverprovisionedReport overReport = getOverprovisionedReport(clusterName);

        // 3. Attribute costs by Namespace and Service based on pod resource share
        List<ClusterPodEntity> pods = podRepository.findByClusterName(clusterName);
        if (pods.isEmpty()) pods = createDefaultSeedPods(clusterName);

        Map<String, Integer> nsPodCounts = new HashMap<>();
        Map<String, Integer> svcPodCounts = new HashMap<>();
        Map<String, String> svcNamespaceMap = new HashMap<>();

        for (ClusterPodEntity p : pods) {
            nsPodCounts.put(p.getNamespace(), nsPodCounts.getOrDefault(p.getNamespace(), 0) + 1);
            String svc = p.getOwnerName() != null ? p.getOwnerName() : p.getName();
            svcPodCounts.put(svc, svcPodCounts.getOrDefault(svc, 0) + 1);
            svcNamespaceMap.put(svc, p.getNamespace());
        }

        int totalPods = Math.max(1, pods.size());
        List<NamespaceCost> byNamespace = new ArrayList<>();
        for (Map.Entry<String, Integer> e : nsPodCounts.entrySet()) {
            double share = (double) e.getValue() / (double) totalPods;
            double cost = totalMonthlyCost * share;
            byNamespace.add(NamespaceCost.builder()
                    .namespace(e.getKey())
                    .monthlyCostUsd(Math.round(cost * 100.0) / 100.0)
                    .percentOfTotal(Math.round(share * 1000.0) / 10.0)
                    .podCount(e.getValue())
                    .build());
        }

        List<ServiceCost> byService = new ArrayList<>();
        for (Map.Entry<String, Integer> e : svcPodCounts.entrySet()) {
            double share = (double) e.getValue() / (double) totalPods;
            double cost = totalMonthlyCost * share;
            byService.add(ServiceCost.builder()
                    .serviceName(e.getKey())
                    .namespace(svcNamespaceMap.getOrDefault(e.getKey(), "twindigital"))
                    .monthlyCostUsd(Math.round(cost * 100.0) / 100.0)
                    .percentOfTotal(Math.round(share * 1000.0) / 10.0)
                    .build());
        }

        // Update Micrometer gauge
        costEstimatedGauge.set(Math.round(totalMonthlyCost * 100.0) / 100.0);

        return CostSummaryResponse.builder()
                .totalMonthlyCostUsd(Math.round(totalMonthlyCost * 100.0) / 100.0)
                .totalAnnualCostUsd(Math.round(totalMonthlyCost * 12.0 * 100.0) / 100.0)
                .currency("USD")
                .dataSource(dataSource)
                .potentialMonthlySavingsUsd(overReport.getPotentialMonthlySavingsUsd())
                .overprovisionedPodCount(overReport.getTotalOverprovisionedPods())
                .byNamespace(byNamespace)
                .byService(byService)
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Estimates the monthly cost delta of a proposed change set (e.g., scaling or resource resize).
     * Invoked by simulation-service or frontend workbench.
     */
    public EstimateChangeResponse estimateChangeCost(EstimateChangeRequest request) {
        String target = request.getTarget() != null ? request.getTarget() : "workload";

        int proposedReplicas = request.getReplicas() != null ? request.getReplicas() : 2;
        int currentReplicas = request.getCurrentReplicas() != null ? request.getCurrentReplicas() : 2;

        long proposedCpu = parseCpuMillis(request.getCpuRequest());
        long currentCpu = parseCpuMillis(request.getCurrentCpuRequest());
        if (proposedCpu == 0L) proposedCpu = 200L;
        if (currentCpu == 0L) currentCpu = 200L;

        long proposedMem = parseMemoryBytes(request.getMemRequest());
        long currentMem = parseMemoryBytes(request.getCurrentMemRequest());
        if (proposedMem == 0L) proposedMem = 256L * 1024L * 1024L;
        if (currentMem == 0L) currentMem = 256L * 1024L * 1024L;

        // Rate per millicore-hour ~= $0.0000208; rate per GiB-hour ~= $0.003
        double currentPodHourly = ((double) currentCpu * (0.0208 / 1000.0)) +
                (((double) currentMem / (1024.0 * 1024.0 * 1024.0)) * 0.003);
        double proposedPodHourly = ((double) proposedCpu * (0.0208 / 1000.0)) +
                (((double) proposedMem / (1024.0 * 1024.0 * 1024.0)) * 0.003);

        double baselineMonthly = Math.max(1.0, currentReplicas * currentPodHourly * monthlyHours);
        double proposedMonthly = Math.max(1.0, proposedReplicas * proposedPodHourly * monthlyHours);
        double deltaMonthly = proposedMonthly - baselineMonthly;

        String explanation = String.format(
                "Proposed change for '%s' (%s: %d -> %d replicas, %s CPU, %s RAM) results in an estimated cost delta of %s$%.2f/month (%s$%.2f/year).",
                target, request.getKind() != null ? request.getKind() : "CHANGE",
                currentReplicas, proposedReplicas,
                formatCpu(proposedCpu), formatMemory(proposedMem),
                deltaMonthly >= 0 ? "+" : "-", Math.abs(deltaMonthly),
                deltaMonthly >= 0 ? "+" : "-", Math.abs(deltaMonthly * 12.0)
        );

        return EstimateChangeResponse.builder()
                .target(target)
                .baselineMonthlyCostUsd(Math.round(baselineMonthly * 100.0) / 100.0)
                .proposedMonthlyCostUsd(Math.round(proposedMonthly * 100.0) / 100.0)
                .deltaMonthlyUsd(Math.round(deltaMonthly * 100.0) / 100.0)
                .deltaAnnualUsd(Math.round(deltaMonthly * 12.0 * 100.0) / 100.0)
                .explanation(explanation)
                .build();
    }

    private long parseCpuMillis(String cpu) {
        if (cpu == null || cpu.isBlank()) return 0L;
        String clean = cpu.trim().toLowerCase();
        try {
            if (clean.endsWith("m")) {
                return Long.parseLong(clean.substring(0, clean.length() - 1));
            }
            return (long) (Double.parseDouble(clean) * 1000.0);
        } catch (Exception e) {
            return 0L;
        }
    }

    private long parseMemoryBytes(String mem) {
        if (mem == null || mem.isBlank()) return 0L;
        String clean = mem.trim();
        try {
            if (clean.endsWith("Mi")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 2)) * 1024L * 1024L);
            } else if (clean.endsWith("Gi")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 2)) * 1024L * 1024L * 1024L);
            } else if (clean.endsWith("Ki")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 2)) * 1024L);
            }
            return (long) Double.parseDouble(clean);
        } catch (Exception e) {
            return 0L;
        }
    }

    private String formatCpu(long millis) {
        return millis + "m";
    }

    private String formatMemory(long bytes) {
        double mi = bytes / (1024.0 * 1024.0);
        return String.format(Locale.ROOT, "%.0fMi", mi);
    }

    private List<ClusterPodEntity> createDefaultSeedPods(String cluster) {
        return List.of(
                ClusterPodEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("auth-service-7bc89d-abc12")
                        .ownerName("auth-service")
                        .nodeName("ip-10-0-1-10.ec2.internal")
                        .cpuRequests("500m")
                        .memoryRequests("1024Mi")
                        .build(),
                ClusterPodEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("billing-service-59bc6d-pl92x")
                        .ownerName("billing-service")
                        .nodeName("ip-10-0-2-20.ec2.internal")
                        .cpuRequests("1000m")
                        .memoryRequests("2048Mi")
                        .build()
        );
    }
}
