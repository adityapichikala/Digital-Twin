package com.twindigital.clustersync.model.mapper;

import com.twindigital.clustersync.model.entity.*;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Component
public class KubernetesResourceMapper {

    /**
     * Map V1Node to NodeEntity.
     */
    public NodeEntity toNodeEntity(String clusterName, V1Node node) {
        if (node == null || node.getMetadata() == null) {
            return null;
        }

        String name = node.getMetadata().getName();
        Map<String, String> labels = node.getMetadata().getLabels() != null
                ? new HashMap<>(node.getMetadata().getLabels())
                : new HashMap<>();

        String allocatableCpu = null;
        String allocatableMem = null;
        String capacityCpu = null;
        String capacityMem = null;
        boolean ready = false;
        String status = "NotReady";
        Map<String, String> conditionsMap = new HashMap<>();

        if (node.getStatus() != null) {
            if (node.getStatus().getAllocatable() != null) {
                Quantity cpu = node.getStatus().getAllocatable().get("cpu");
                Quantity mem = node.getStatus().getAllocatable().get("memory");
                allocatableCpu = cpu != null ? formatCpu(cpu) : null;
                allocatableMem = mem != null ? formatMemory(mem) : null;
            }
            if (node.getStatus().getCapacity() != null) {
                Quantity cpu = node.getStatus().getCapacity().get("cpu");
                Quantity mem = node.getStatus().getCapacity().get("memory");
                capacityCpu = cpu != null ? formatCpu(cpu) : null;
                capacityMem = mem != null ? formatMemory(mem) : null;
            }
            if (node.getStatus().getConditions() != null) {
                for (V1NodeCondition cond : node.getStatus().getConditions()) {
                    if (cond.getType() != null) {
                        conditionsMap.put(cond.getType(), cond.getStatus());
                        if ("Ready".equalsIgnoreCase(cond.getType())) {
                            ready = "True".equalsIgnoreCase(cond.getStatus());
                            status = ready ? "Ready" : "NotReady";
                        }
                    }
                }
            }
        }

        return NodeEntity.builder()
                .clusterName(clusterName)
                .name(name)
                .allocatableCpu(allocatableCpu)
                .allocatableMemory(allocatableMem)
                .capacityCpu(capacityCpu)
                .capacityMemory(capacityMem)
                .ready(ready)
                .status(status)
                .labels(labels)
                .conditions(conditionsMap)
                .lastSyncedAt(Instant.now())
                .build();
    }

    /**
     * Map V1Pod to PodEntity.
     */
    public PodEntity toPodEntity(String clusterName, V1Pod pod) {
        if (pod == null || pod.getMetadata() == null) {
            return null;
        }

        String namespace = pod.getMetadata().getNamespace() != null ? pod.getMetadata().getNamespace() : "default";
        String name = pod.getMetadata().getName();
        String nodeName = pod.getSpec() != null ? pod.getSpec().getNodeName() : null;

        Map<String, String> labels = pod.getMetadata().getLabels() != null
                ? new HashMap<>(pod.getMetadata().getLabels())
                : new HashMap<>();

        String ownerKind = null;
        String ownerName = null;
        if (pod.getMetadata().getOwnerReferences() != null && !pod.getMetadata().getOwnerReferences().isEmpty()) {
            V1OwnerReference ref = pod.getMetadata().getOwnerReferences().get(0);
            ownerKind = ref.getKind();
            ownerName = ref.getName();
            if ("ReplicaSet".equalsIgnoreCase(ownerKind) && ownerName != null) {
                String hash = labels.get("pod-template-hash");
                if (hash != null && ownerName.endsWith("-" + hash)) {
                    ownerName = ownerName.substring(0, ownerName.length() - hash.length() - 1);
                } else {
                    int lastDash = ownerName.lastIndexOf('-');
                    if (lastDash > 0) {
                        ownerName = ownerName.substring(0, lastDash);
                    }
                }
                ownerKind = "Deployment";
            }
        }

        // Aggregate container resource requests and limits
        String cpuRequests = null;
        String memoryRequests = null;
        String cpuLimits = null;
        String memoryLimits = null;

        if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            BigDecimal totalCpuReq = BigDecimal.ZERO;
            BigDecimal totalMemReqBytes = BigDecimal.ZERO;
            BigDecimal totalCpuLim = BigDecimal.ZERO;
            BigDecimal totalMemLimBytes = BigDecimal.ZERO;
            boolean hasCpuReq = false, hasMemReq = false, hasCpuLim = false, hasMemLim = false;

            for (V1Container c : pod.getSpec().getContainers()) {
                if (c.getResources() != null) {
                    if (c.getResources().getRequests() != null) {
                        Quantity cpu = c.getResources().getRequests().get("cpu");
                        if (cpu != null && cpu.getNumber() != null) {
                            totalCpuReq = totalCpuReq.add(cpu.getNumber());
                            hasCpuReq = true;
                        }
                        Quantity mem = c.getResources().getRequests().get("memory");
                        if (mem != null && mem.getNumber() != null) {
                            totalMemReqBytes = totalMemReqBytes.add(mem.getNumber());
                            hasMemReq = true;
                        }
                    }
                    if (c.getResources().getLimits() != null) {
                        Quantity cpu = c.getResources().getLimits().get("cpu");
                        if (cpu != null && cpu.getNumber() != null) {
                            totalCpuLim = totalCpuLim.add(cpu.getNumber());
                            hasCpuLim = true;
                        }
                        Quantity mem = c.getResources().getLimits().get("memory");
                        if (mem != null && mem.getNumber() != null) {
                            totalMemLimBytes = totalMemLimBytes.add(mem.getNumber());
                            hasMemLim = true;
                        }
                    }
                }
            }
            if (hasCpuReq) cpuRequests = formatCpu(totalCpuReq);
            if (hasMemReq) memoryRequests = formatMemory(totalMemReqBytes);
            if (hasCpuLim) cpuLimits = formatCpu(totalCpuLim);
            if (hasMemLim) memoryLimits = formatMemory(totalMemLimBytes);
        }

        String phase = "Unknown";
        int restarts = 0;
        if (pod.getStatus() != null) {
            if (pod.getStatus().getPhase() != null) {
                phase = pod.getStatus().getPhase();
            }
            if (pod.getStatus().getContainerStatuses() != null) {
                for (V1ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                    if (cs.getRestartCount() != null) {
                        restarts += cs.getRestartCount();
                    }
                }
            }
        }

        return PodEntity.builder()
                .clusterName(clusterName)
                .namespace(namespace)
                .name(name)
                .nodeName(nodeName)
                .ownerKind(ownerKind)
                .ownerName(ownerName)
                .cpuRequests(cpuRequests)
                .memoryRequests(memoryRequests)
                .cpuLimits(cpuLimits)
                .memoryLimits(memoryLimits)
                .phase(phase)
                .restarts(restarts)
                .labels(labels)
                .lastSyncedAt(Instant.now())
                .build();
    }

    /**
     * Map V1Service to ServiceEntity.
     */
    public ServiceEntity toServiceEntity(String clusterName, V1Service service) {
        if (service == null || service.getMetadata() == null) {
            return null;
        }

        String namespace = service.getMetadata().getNamespace() != null ? service.getMetadata().getNamespace() : "default";
        String name = service.getMetadata().getName();
        Map<String, String> labels = service.getMetadata().getLabels() != null
                ? new HashMap<>(service.getMetadata().getLabels())
                : new HashMap<>();
        Map<String, String> annotations = service.getMetadata().getAnnotations() != null
                ? new HashMap<>(service.getMetadata().getAnnotations())
                : new HashMap<>();

        String serviceType = "ClusterIP";
        String clusterIp = null;
        Map<String, String> selector = new HashMap<>();
        List<Map<String, Object>> ports = new ArrayList<>();

        if (service.getSpec() != null) {
            if (service.getSpec().getType() != null) {
                serviceType = service.getSpec().getType();
            }
            clusterIp = service.getSpec().getClusterIP();
            if (service.getSpec().getSelector() != null) {
                selector.putAll(service.getSpec().getSelector());
            }
            if (service.getSpec().getPorts() != null) {
                for (V1ServicePort sp : service.getSpec().getPorts()) {
                    Map<String, Object> portMap = new HashMap<>();
                    portMap.put("name", sp.getName());
                    portMap.put("port", sp.getPort());
                    portMap.put("protocol", sp.getProtocol());
                    if (sp.getTargetPort() != null) {
                        portMap.put("targetPort", sp.getTargetPort().toString());
                    }
                    if (sp.getNodePort() != null) {
                        portMap.put("nodePort", sp.getNodePort());
                    }
                    ports.add(portMap);
                }
            }
        }

        return ServiceEntity.builder()
                .clusterName(clusterName)
                .namespace(namespace)
                .name(name)
                .serviceType(serviceType)
                .clusterIp(clusterIp)
                .selector(selector)
                .ports(ports)
                .labels(labels)
                .annotations(annotations)
                .lastSyncedAt(Instant.now())
                .build();
    }

    /**
     * Map V1Deployment to DeploymentEntity.
     */
    public DeploymentEntity toDeploymentEntity(String clusterName, V1Deployment deployment) {
        if (deployment == null || deployment.getMetadata() == null) {
            return null;
        }

        String namespace = deployment.getMetadata().getNamespace() != null ? deployment.getMetadata().getNamespace() : "default";
        String name = deployment.getMetadata().getName();
        Map<String, String> labels = deployment.getMetadata().getLabels() != null
                ? new HashMap<>(deployment.getMetadata().getLabels())
                : new HashMap<>();
        Map<String, String> annotations = deployment.getMetadata().getAnnotations() != null
                ? new HashMap<>(deployment.getMetadata().getAnnotations())
                : new HashMap<>();

        int replicas = 0;
        int readyReplicas = 0;
        int availableReplicas = 0;
        int updatedReplicas = 0;
        Map<String, String> selector = new HashMap<>();

        if (deployment.getSpec() != null) {
            if (deployment.getSpec().getReplicas() != null) {
                replicas = deployment.getSpec().getReplicas();
            }
            if (deployment.getSpec().getSelector() != null && deployment.getSpec().getSelector().getMatchLabels() != null) {
                selector.putAll(deployment.getSpec().getSelector().getMatchLabels());
            }
        }

        if (deployment.getStatus() != null) {
            if (deployment.getStatus().getReadyReplicas() != null) {
                readyReplicas = deployment.getStatus().getReadyReplicas();
            }
            if (deployment.getStatus().getAvailableReplicas() != null) {
                availableReplicas = deployment.getStatus().getAvailableReplicas();
            }
            if (deployment.getStatus().getUpdatedReplicas() != null) {
                updatedReplicas = deployment.getStatus().getUpdatedReplicas();
            }
        }

        return DeploymentEntity.builder()
                .clusterName(clusterName)
                .namespace(namespace)
                .name(name)
                .replicas(replicas)
                .readyReplicas(readyReplicas)
                .availableReplicas(availableReplicas)
                .updatedReplicas(updatedReplicas)
                .selector(selector)
                .labels(labels)
                .annotations(annotations)
                .lastSyncedAt(Instant.now())
                .build();
    }

    /**
     * Map V2HorizontalPodAutoscaler to HpaEntity.
     */
    public HpaEntity toHpaEntity(String clusterName, V2HorizontalPodAutoscaler hpa) {
        if (hpa == null || hpa.getMetadata() == null) {
            return null;
        }

        String namespace = hpa.getMetadata().getNamespace() != null ? hpa.getMetadata().getNamespace() : "default";
        String name = hpa.getMetadata().getName();
        Map<String, String> labels = hpa.getMetadata().getLabels() != null
                ? new HashMap<>(hpa.getMetadata().getLabels())
                : new HashMap<>();

        String targetKind = null;
        String targetName = null;
        Integer minReplicas = 1;
        int maxReplicas = 1;
        Integer currentReplicas = null;
        Integer desiredReplicas = null;
        Integer currentCpuUtil = null;
        Integer targetCpuUtil = null;

        if (hpa.getSpec() != null) {
            if (hpa.getSpec().getScaleTargetRef() != null) {
                targetKind = hpa.getSpec().getScaleTargetRef().getKind();
                targetName = hpa.getSpec().getScaleTargetRef().getName();
            }
            minReplicas = hpa.getSpec().getMinReplicas();
            if (hpa.getSpec().getMaxReplicas() != null) {
                maxReplicas = hpa.getSpec().getMaxReplicas();
            }
            if (hpa.getSpec().getMetrics() != null) {
                for (V2MetricSpec m : hpa.getSpec().getMetrics()) {
                    if ("Resource".equalsIgnoreCase(m.getType()) && m.getResource() != null
                            && "cpu".equalsIgnoreCase(m.getResource().getName())
                            && m.getResource().getTarget() != null) {
                        targetCpuUtil = m.getResource().getTarget().getAverageUtilization();
                    }
                }
            }
        }

        if (hpa.getStatus() != null) {
            currentReplicas = hpa.getStatus().getCurrentReplicas();
            desiredReplicas = hpa.getStatus().getDesiredReplicas();
            if (hpa.getStatus().getCurrentMetrics() != null) {
                for (V2MetricStatus m : hpa.getStatus().getCurrentMetrics()) {
                    if ("Resource".equalsIgnoreCase(m.getType()) && m.getResource() != null
                            && "cpu".equalsIgnoreCase(m.getResource().getName())
                            && m.getResource().getCurrent() != null) {
                        currentCpuUtil = m.getResource().getCurrent().getAverageUtilization();
                    }
                }
            }
        }

        return HpaEntity.builder()
                .clusterName(clusterName)
                .namespace(namespace)
                .name(name)
                .targetKind(targetKind)
                .targetName(targetName)
                .minReplicas(minReplicas)
                .maxReplicas(maxReplicas)
                .currentReplicas(currentReplicas)
                .desiredReplicas(desiredReplicas)
                .currentCpuUtilization(currentCpuUtil)
                .targetCpuUtilization(targetCpuUtil)
                .labels(labels)
                .lastSyncedAt(Instant.now())
                .build();
    }

    /**
     * Map V1PodDisruptionBudget to PdbEntity.
     */
    public PdbEntity toPdbEntity(String clusterName, V1PodDisruptionBudget pdb) {
        if (pdb == null || pdb.getMetadata() == null) {
            return null;
        }

        String namespace = pdb.getMetadata().getNamespace() != null ? pdb.getMetadata().getNamespace() : "default";
        String name = pdb.getMetadata().getName();
        Map<String, String> labels = pdb.getMetadata().getLabels() != null
                ? new HashMap<>(pdb.getMetadata().getLabels())
                : new HashMap<>();

        String minAvailable = null;
        String maxUnavailable = null;
        Map<String, String> selector = new HashMap<>();

        if (pdb.getSpec() != null) {
            if (pdb.getSpec().getMinAvailable() != null) {
                minAvailable = pdb.getSpec().getMinAvailable().toString();
            }
            if (pdb.getSpec().getMaxUnavailable() != null) {
                maxUnavailable = pdb.getSpec().getMaxUnavailable().toString();
            }
            if (pdb.getSpec().getSelector() != null && pdb.getSpec().getSelector().getMatchLabels() != null) {
                selector.putAll(pdb.getSpec().getSelector().getMatchLabels());
            }
        }

        Integer currentHealthy = null;
        Integer desiredHealthy = null;
        Integer disruptionsAllowed = null;
        Integer expectedPods = null;

        if (pdb.getStatus() != null) {
            currentHealthy = pdb.getStatus().getCurrentHealthy();
            desiredHealthy = pdb.getStatus().getDesiredHealthy();
            disruptionsAllowed = pdb.getStatus().getDisruptionsAllowed();
            expectedPods = pdb.getStatus().getExpectedPods();
        }

        return PdbEntity.builder()
                .clusterName(clusterName)
                .namespace(namespace)
                .name(name)
                .minAvailable(minAvailable)
                .maxUnavailable(maxUnavailable)
                .currentHealthy(currentHealthy)
                .desiredHealthy(desiredHealthy)
                .disruptionsAllowed(disruptionsAllowed)
                .expectedPods(expectedPods)
                .selector(selector)
                .labels(labels)
                .lastSyncedAt(Instant.now())
                .build();
    }

    public String formatCpu(Quantity cpu) {
        if (cpu == null || cpu.getNumber() == null) {
            return null;
        }
        return formatCpu(cpu.getNumber());
    }

    public String formatCpu(BigDecimal cores) {
        if (cores == null) {
            return null;
        }
        long millis = cores.multiply(BigDecimal.valueOf(1000))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValue();
        return millis + "m";
    }

    public String formatMemory(Quantity mem) {
        if (mem == null || mem.getNumber() == null) {
            return null;
        }
        return formatMemory(mem.getNumber());
    }

    public String formatMemory(BigDecimal bytes) {
        if (bytes == null || bytes.compareTo(BigDecimal.ZERO) <= 0) {
            return "0Mi";
        }
        long b = bytes.longValue();
        if (b % (1024L * 1024L) == 0) {
            return (b / (1024L * 1024L)) + "Mi";
        }
        if (b % 1024L == 0) {
            return (b / 1024L) + "Ki";
        }
        if (b >= 1024L * 1024L) {
            return (b / (1024L * 1024L)) + "Mi";
        }
        return Math.max(1, b / 1024L) + "Ki";
    }
}
