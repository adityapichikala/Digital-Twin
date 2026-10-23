package com.twindigital.topology.service;

import com.twindigital.topology.entity.*;
import com.twindigital.topology.model.TopologyDTOs.*;
import com.twindigital.topology.repository.TopologyRepositories;
import com.twindigital.topology.repository.TopologyRepositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopologyGraphService {

    private final ClusterNodeRepository nodeRepository;
    private final ClusterPodRepository podRepository;
    private final ClusterDeploymentRepository deploymentRepository;
    private final ClusterServiceRepository serviceRepository;
    private final ClusterSyncStateRepository syncStateRepository;
    private final ServiceDependencyRepository dependencyRepository;

    private static final String DEFAULT_CLUSTER = "twindigital-cluster";

    public TopologyResponse getTopology(String cluster) {
        String clusterName = (cluster != null && !cluster.isBlank()) ? cluster : DEFAULT_CLUSTER;

        List<ClusterNodeEntity> dbNodes = nodeRepository.findByClusterName(clusterName);
        List<ClusterPodEntity> dbPods = podRepository.findByClusterName(clusterName);
        List<ClusterDeploymentEntity> dbDeployments = deploymentRepository.findByClusterName(clusterName);
        List<ClusterServiceEntity> dbServices = serviceRepository.findByClusterName(clusterName);

        List<TopologyNode> nodes = new ArrayList<>();
        List<TopologyEdge> edges = new ArrayList<>();

        if (dbNodes.isEmpty() && dbPods.isEmpty() && dbDeployments.isEmpty() && dbServices.isEmpty()) {
            // Provide realistic seed topology for TwinDigital EKS cluster
            populateDefaultTopology(nodes, edges);
        } else {
            buildFromDatabase(clusterName, dbNodes, dbPods, dbDeployments, dbServices, nodes, edges);
        }

        // Calculate health metrics
        ClusterHealthSummary health = computeClusterHealth(nodes);

        // Calculate staleness
        StalenessInfo staleness = computeStaleness(clusterName);

        return TopologyResponse.builder()
                .nodes(nodes)
                .edges(edges)
                .health(health)
                .staleness(staleness)
                .build();
    }

    public BlastRadiusResponse calculateBlastRadius(String cluster, String targetServiceName) {
        if (targetServiceName == null || targetServiceName.isBlank()) {
            throw new IllegalArgumentException("target parameter is required");
        }

        TopologyResponse topology = getTopology(cluster);
        String cleanTarget = targetServiceName.trim().toLowerCase();

        // Find target node (matching name or ID)
        TopologyNode targetNode = topology.getNodes().stream()
                .filter(n -> n.getName().equalsIgnoreCase(cleanTarget) ||
                             n.getId().toLowerCase().contains(":" + cleanTarget) ||
                             n.getId().toLowerCase().contains("/" + cleanTarget))
                .findFirst()
                .orElse(null);

        if (targetNode == null) {
            // Create fallback target descriptor
            targetNode = TopologyNode.builder()
                    .id("service:twindigital/" + cleanTarget)
                    .name(cleanTarget)
                    .type("SERVICE")
                    .health("HEALTHY")
                    .healthColor("#10b981")
                    .build();
        }

        // Build adjacency maps for dependency edges
        // dependency edge: caller (source) -> provider (target)
        // Upstream of T: callers that call T (edges where target == T)
        // Downstream of T: dependencies called by T (edges where source == T)
        Map<String, List<String>> incomingDependencies = new HashMap<>(); // target -> sources (upstream)
        Map<String, List<String>> outgoingDependencies = new HashMap<>(); // source -> targets (downstream)
        Map<String, TopologyNode> nodeLookup = topology.getNodes().stream()
                .collect(Collectors.toMap(TopologyNode::getId, n -> n, (a, b) -> a));

        Set<String> dependencyEdgeIds = new HashSet<>();

        for (TopologyEdge edge : topology.getEdges()) {
            if ("DEPENDENCY".equalsIgnoreCase(edge.getType()) || "INGRESS".equalsIgnoreCase(edge.getType())) {
                outgoingDependencies.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
                incomingDependencies.computeIfAbsent(edge.getTarget(), k -> new ArrayList<>()).add(edge.getSource());
                dependencyEdgeIds.add(edge.getId());
            }
        }

        // Traverse Upstream (callers affected if target fails)
        Set<String> upstreamIds = new LinkedHashSet<>();
        Queue<String> upQueue = new LinkedList<>();
        upQueue.add(targetNode.getId());

        while (!upQueue.isEmpty()) {
            String curr = upQueue.poll();
            List<String> callers = incomingDependencies.getOrDefault(curr, Collections.emptyList());
            for (String caller : callers) {
                if (!caller.equals(targetNode.getId()) && upstreamIds.add(caller)) {
                    upQueue.add(caller);
                }
            }
        }

        // Traverse Downstream (dependencies target relies upon)
        Set<String> downstreamIds = new LinkedHashSet<>();
        Queue<String> downQueue = new LinkedList<>();
        downQueue.add(targetNode.getId());

        while (!downQueue.isEmpty()) {
            String curr = downQueue.poll();
            List<String> deps = outgoingDependencies.getOrDefault(curr, Collections.emptyList());
            for (String dep : deps) {
                if (!dep.equals(targetNode.getId()) && downstreamIds.add(dep)) {
                    downQueue.add(dep);
                }
            }
        }

        List<TopologyNode> upstreamList = upstreamIds.stream()
                .map(id -> nodeLookup.getOrDefault(id, TopologyNode.builder().id(id).name(id).type("SERVICE").health("HEALTHY").build()))
                .collect(Collectors.toList());

        List<TopologyNode> downstreamList = downstreamIds.stream()
                .map(id -> nodeLookup.getOrDefault(id, TopologyNode.builder().id(id).name(id).type("SERVICE").health("HEALTHY").build()))
                .collect(Collectors.toList());

        Set<String> allImpactedNodeIds = new HashSet<>();
        allImpactedNodeIds.add(targetNode.getId());
        allImpactedNodeIds.addAll(upstreamIds);
        allImpactedNodeIds.addAll(downstreamIds);

        // Include associated pods/deployments of impacted services
        for (TopologyEdge edge : topology.getEdges()) {
            if (allImpactedNodeIds.contains(edge.getSource()) || allImpactedNodeIds.contains(edge.getTarget())) {
                allImpactedNodeIds.add(edge.getSource());
                allImpactedNodeIds.add(edge.getTarget());
            }
        }

        Set<String> impactedEdgeIds = topology.getEdges().stream()
                .filter(e -> allImpactedNodeIds.contains(e.getSource()) && allImpactedNodeIds.contains(e.getTarget()))
                .map(TopologyEdge::getId)
                .collect(Collectors.toSet());

        int totalServicesImpacted = upstreamList.size() + downstreamList.size();
        String riskScore;
        if (cleanTarget.contains("auth") || cleanTarget.contains("gateway") || totalServicesImpacted >= 4) {
            riskScore = "CRITICAL";
        } else if (totalServicesImpacted >= 3) {
            riskScore = "HIGH";
        } else if (totalServicesImpacted >= 1) {
            riskScore = "MEDIUM";
        } else {
            riskScore = "LOW";
        }

        String explanation = String.format(
                "Degradation or failure of '%s' will impact %d upstream caller(s) (%s) and rely on %d downstream dependenc(ies) (%s). Total service blast radius: %d.",
                targetNode.getName(),
                upstreamList.size(),
                upstreamList.stream().map(TopologyNode::getName).collect(Collectors.joining(", ")),
                downstreamList.size(),
                downstreamList.stream().map(TopologyNode::getName).collect(Collectors.joining(", ")),
                totalServicesImpacted
        );

        return BlastRadiusResponse.builder()
                .target(targetNode)
                .upstream(upstreamList)
                .downstream(downstreamList)
                .impactedNodeIds(allImpactedNodeIds)
                .impactedEdgeIds(impactedEdgeIds)
                .totalImpacted(totalServicesImpacted)
                .riskScore(riskScore)
                .explanation(explanation)
                .build();
    }

    private void buildFromDatabase(String clusterName,
                                   List<ClusterNodeEntity> dbNodes,
                                   List<ClusterPodEntity> dbPods,
                                   List<ClusterDeploymentEntity> dbDeployments,
                                   List<ClusterServiceEntity> dbServices,
                                   List<TopologyNode> outNodes,
                                   List<TopologyEdge> outEdges) {

        // 1. Add Kubernetes Nodes
        for (ClusterNodeEntity n : dbNodes) {
            String nodeId = "node:" + n.getName();
            boolean ready = n.isReady();
            String health = ready ? "HEALTHY" : "CRITICAL";

            Map<String, Object> details = new HashMap<>();
            details.put("allocatableCpu", n.getAllocatableCpu());
            details.put("allocatableMemory", n.getAllocatableMemory());
            details.put("capacityCpu", n.getCapacityCpu());
            details.put("capacityMemory", n.getCapacityMemory());
            details.put("ready", ready);
            details.put("status", n.getStatus());

            outNodes.add(TopologyNode.builder()
                    .id(nodeId)
                    .name(n.getName())
                    .type("NODE")
                    .health(health)
                    .healthColor(getHealthColor(health))
                    .details(details)
                    .labels(n.getLabels())
                    .build());
        }

        // 2. Add Deployments
        Map<String, String> deployIdMap = new HashMap<>(); // ns/name -> deployId
        for (ClusterDeploymentEntity d : dbDeployments) {
            String deployId = "deployment:" + d.getNamespace() + "/" + d.getName();
            deployIdMap.put(d.getNamespace() + "/" + d.getName(), deployId);

            String health = "HEALTHY";
            if (d.getReplicas() > 0 && d.getReadyReplicas() == 0) {
                health = "CRITICAL";
            } else if (d.getReadyReplicas() < d.getReplicas()) {
                health = "DEGRADED";
            }

            Map<String, Object> details = new HashMap<>();
            details.put("replicas", d.getReplicas());
            details.put("readyReplicas", d.getReadyReplicas());
            details.put("availableReplicas", d.getAvailableReplicas());
            details.put("selector", d.getSelector());

            outNodes.add(TopologyNode.builder()
                    .id(deployId)
                    .name(d.getName())
                    .namespace(d.getNamespace())
                    .type("DEPLOYMENT")
                    .health(health)
                    .healthColor(getHealthColor(health))
                    .details(details)
                    .labels(d.getLabels())
                    .build());
        }

        // 3. Add Pods
        for (ClusterPodEntity p : dbPods) {
            String podId = "pod:" + p.getNamespace() + "/" + p.getName();

            String health = "HEALTHY";
            if (p.getRestarts() > 3 || !"Running".equalsIgnoreCase(p.getPhase())) {
                health = "CRITICAL";
            } else if (p.getRestarts() > 0) {
                health = "DEGRADED";
            }

            Map<String, Object> details = new HashMap<>();
            details.put("cpuRequests", p.getCpuRequests());
            details.put("cpuLimits", p.getCpuLimits());
            details.put("memoryRequests", p.getMemoryRequests());
            details.put("memoryLimits", p.getMemoryLimits());
            details.put("phase", p.getPhase());
            details.put("restarts", p.getRestarts());
            details.put("nodeName", p.getNodeName());
            details.put("ownerKind", p.getOwnerKind());
            details.put("ownerName", p.getOwnerName());

            outNodes.add(TopologyNode.builder()
                    .id(podId)
                    .name(p.getName())
                    .namespace(p.getNamespace())
                    .type("POD")
                    .health(health)
                    .healthColor(getHealthColor(health))
                    .details(details)
                    .labels(p.getLabels())
                    .build());

            // Edge: Node -> Pod ("runs on")
            if (p.getNodeName() != null && !p.getNodeName().isBlank()) {
                String sourceNodeId = "node:" + p.getNodeName();
                outEdges.add(TopologyEdge.builder()
                        .id("e:node-pod:" + p.getNodeName() + "_" + p.getName())
                        .source(sourceNodeId)
                        .target(podId)
                        .type("HIERARCHY")
                        .label("runs on")
                        .build());
            }

            // Edge: Deployment -> Pod ("manages")
            if (p.getOwnerName() != null && !p.getOwnerName().isBlank()) {
                for (ClusterDeploymentEntity d : dbDeployments) {
                    if (d.getNamespace().equals(p.getNamespace()) && p.getName().startsWith(d.getName())) {
                        String deployId = "deployment:" + d.getNamespace() + "/" + d.getName();
                        outEdges.add(TopologyEdge.builder()
                                .id("e:dep-pod:" + d.getName() + "_" + p.getName())
                                .source(deployId)
                                .target(podId)
                                .type("HIERARCHY")
                                .label("manages")
                                .build());
                        break;
                    }
                }
            }
        }

        // 4. Add Services
        for (ClusterServiceEntity s : dbServices) {
            String svcId = "service:" + s.getNamespace() + "/" + s.getName();

            Map<String, Object> details = new HashMap<>();
            details.put("serviceType", s.getServiceType());
            details.put("clusterIp", s.getClusterIp());
            details.put("ports", s.getPorts());
            details.put("selector", s.getSelector());

            outNodes.add(TopologyNode.builder()
                    .id(svcId)
                    .name(s.getName())
                    .namespace(s.getNamespace())
                    .type("SERVICE")
                    .health("HEALTHY")
                    .healthColor(getHealthColor("HEALTHY"))
                    .details(details)
                    .labels(s.getLabels())
                    .build());

            // Edge: Service -> Deployment ("routes to")
            for (ClusterDeploymentEntity d : dbDeployments) {
                if (d.getNamespace().equals(s.getNamespace()) &&
                    (d.getName().equals(s.getName()) || matchSelector(s.getSelector(), d.getLabels()))) {
                    String deployId = "deployment:" + d.getNamespace() + "/" + d.getName();
                    outEdges.add(TopologyEdge.builder()
                            .id("e:svc-dep:" + s.getName() + "_" + d.getName())
                            .source(svcId)
                            .target(deployId)
                            .type("HIERARCHY")
                            .label("routes to")
                            .build());
                }
            }
        }

        // 5. Service-to-Service Dependencies inferred from annotations and database
        inferServiceDependencies(clusterName, dbServices, dbDeployments, outEdges);
    }

    private void inferServiceDependencies(String clusterName,
                                          List<ClusterServiceEntity> services,
                                          List<ClusterDeploymentEntity> deployments,
                                          List<TopologyEdge> outEdges) {
        // (a) Optional annotation `twindigital.io/depends-on` on Deployments or Services
        for (ClusterDeploymentEntity dep : deployments) {
            String dependsOn = null;
            if (dep.getAnnotations() != null && dep.getAnnotations().containsKey("twindigital.io/depends-on")) {
                dependsOn = dep.getAnnotations().get("twindigital.io/depends-on");
            } else if (dep.getLabels() != null && dep.getLabels().containsKey("twindigital.io/depends-on")) {
                dependsOn = dep.getLabels().get("twindigital.io/depends-on");
            }

            if (dependsOn != null && !dependsOn.isBlank()) {
                String[] targets = dependsOn.split(",");
                for (String t : targets) {
                    String cleanTarget = t.trim();
                    if (!cleanTarget.isEmpty()) {
                        outEdges.add(TopologyEdge.builder()
                                .id("e:dep-annot:" + dep.getName() + "_" + cleanTarget)
                                .source("service:" + dep.getNamespace() + "/" + dep.getName())
                                .target("service:" + dep.getNamespace() + "/" + cleanTarget)
                                .type("DEPENDENCY")
                                .label("depends on")
                                .animated(true)
                                .build());
                    }
                }
            }
        }

        // (b) Persisted dependency rules from database
        List<ServiceDependencyEntity> dbDeps = dependencyRepository.findByClusterName(clusterName);
        for (ServiceDependencyEntity dep : dbDeps) {
            outEdges.add(TopologyEdge.builder()
                    .id("e:db-dep:" + dep.getSourceService() + "_" + dep.getTargetService())
                    .source("service:twindigital/" + dep.getSourceService())
                    .target("service:twindigital/" + dep.getTargetService())
                    .type("DEPENDENCY")
                    .label(dep.getDescription() != null ? dep.getDescription() : "depends on")
                    .animated(true)
                    .build());
        }

        // (c) Standard API Gateway Ingress Routes
        outEdges.add(TopologyEdge.builder()
                .id("e:gateway-auth")
                .source("service:twindigital/gateway")
                .target("service:twindigital/auth-service")
                .type("INGRESS")
                .label("verifies JWT")
                .animated(true)
                .build());
        outEdges.add(TopologyEdge.builder()
                .id("e:gateway-sim")
                .source("service:twindigital/gateway")
                .target("service:twindigital/simulation-service")
                .type("INGRESS")
                .label("proxies /api/v1/simulations")
                .animated(true)
                .build());
        outEdges.add(TopologyEdge.builder()
                .id("e:gateway-topo")
                .source("service:twindigital/gateway")
                .target("service:twindigital/topology-service")
                .type("INGRESS")
                .label("proxies /api/v1/topology")
                .animated(true)
                .build());
        outEdges.add(TopologyEdge.builder()
                .id("e:sim-topo")
                .source("service:twindigital/simulation-service")
                .target("service:twindigital/topology-service")
                .type("DEPENDENCY")
                .label("queries graph")
                .animated(true)
                .build());
        outEdges.add(TopologyEdge.builder()
                .id("e:sim-cost")
                .source("service:twindigital/simulation-service")
                .target("service:twindigital/cost-risk-service")
                .type("DEPENDENCY")
                .label("calculates delta")
                .animated(true)
                .build());
        outEdges.add(TopologyEdge.builder()
                .id("e:topo-sync")
                .source("service:twindigital/topology-service")
                .target("service:twindigital/cluster-sync-service")
                .type("DEPENDENCY")
                .label("consumes snapshot")
                .animated(true)
                .build());
    }

    private void populateDefaultTopology(List<TopologyNode> outNodes, List<TopologyEdge> outEdges) {
        // EKS Worker Nodes
        outNodes.add(createNode("node:ip-10-0-1-50", "ip-10-0-1-50.ec2.internal", "NODE", "HEALTHY",
                Map.of("allocatableCpu", "1930m", "allocatableMemory", "3800Mi", "instanceType", "t3.medium", "zone", "ap-south-1a", "ready", true)));
        outNodes.add(createNode("node:ip-10-0-2-80", "ip-10-0-2-80.ec2.internal", "NODE", "HEALTHY",
                Map.of("allocatableCpu", "1930m", "allocatableMemory", "3800Mi", "instanceType", "t3.medium", "zone", "ap-south-1b", "ready", true)));

        // Ingress
        outNodes.add(createNode("ingress:gateway-alb", "twindigital-alb", "INGRESS", "HEALTHY",
                Map.of("protocol", "HTTPS", "port", 443, "host", "twindigital.io")));

        // Services
        outNodes.add(createNode("service:twindigital/gateway", "gateway", "SERVICE", "HEALTHY",
                Map.of("serviceType", "ClusterIP", "port", 3000, "clusterIp", "172.20.10.15")));
        outNodes.add(createNode("service:twindigital/auth-service", "auth-service", "SERVICE", "HEALTHY",
                Map.of("serviceType", "ClusterIP", "port", 8081, "clusterIp", "172.20.10.16")));
        outNodes.add(createNode("service:twindigital/cluster-sync-service", "cluster-sync-service", "SERVICE", "HEALTHY",
                Map.of("serviceType", "ClusterIP", "port", 8082, "clusterIp", "172.20.10.17")));
        outNodes.add(createNode("service:twindigital/topology-service", "topology-service", "SERVICE", "HEALTHY",
                Map.of("serviceType", "ClusterIP", "port", 8083, "clusterIp", "172.20.10.18")));
        outNodes.add(createNode("service:twindigital/simulation-service", "simulation-service", "SERVICE", "HEALTHY",
                Map.of("serviceType", "ClusterIP", "port", 8084, "clusterIp", "172.20.10.19")));
        outNodes.add(createNode("service:twindigital/cost-risk-service", "cost-risk-service", "SERVICE", "HEALTHY",
                Map.of("serviceType", "ClusterIP", "port", 8085, "clusterIp", "172.20.10.20")));

        // Deployments
        outNodes.add(createNode("deployment:twindigital/gateway", "gateway", "DEPLOYMENT", "HEALTHY",
                Map.of("replicas", 2, "readyReplicas", 2, "availableReplicas", 2)));
        outNodes.add(createNode("deployment:twindigital/auth-service", "auth-service", "DEPLOYMENT", "HEALTHY",
                Map.of("replicas", 2, "readyReplicas", 2, "availableReplicas", 2)));
        outNodes.add(createNode("deployment:twindigital/topology-service", "topology-service", "DEPLOYMENT", "HEALTHY",
                Map.of("replicas", 1, "readyReplicas", 1, "availableReplicas", 1)));
        outNodes.add(createNode("deployment:twindigital/simulation-service", "simulation-service", "DEPLOYMENT", "HEALTHY",
                Map.of("replicas", 2, "readyReplicas", 2, "availableReplicas", 2)));
        outNodes.add(createNode("deployment:twindigital/cost-risk-service", "cost-risk-service", "DEPLOYMENT", "HEALTHY",
                Map.of("replicas", 1, "readyReplicas", 1, "availableReplicas", 1)));

        // Pods
        outNodes.add(createNode("pod:twindigital/gateway-67f8b-x89k1", "gateway-67f8b-x89k1", "POD", "HEALTHY",
                Map.of("cpuRequests", "100m", "cpuLimits", "250m", "memoryRequests", "128Mi", "memoryLimits", "256Mi", "restarts", 0, "phase", "Running", "nodeName", "ip-10-0-1-50.ec2.internal")));
        outNodes.add(createNode("pod:twindigital/auth-service-789a-k2j34", "auth-service-789a-k2j34", "POD", "HEALTHY",
                Map.of("cpuRequests", "200m", "cpuLimits", "500m", "memoryRequests", "256Mi", "memoryLimits", "512Mi", "restarts", 0, "phase", "Running", "nodeName", "ip-10-0-1-50.ec2.internal")));
        outNodes.add(createNode("pod:twindigital/topology-service-541c-m91a2", "topology-service-541c-m91a2", "POD", "HEALTHY",
                Map.of("cpuRequests", "200m", "cpuLimits", "500m", "memoryRequests", "256Mi", "memoryLimits", "512Mi", "restarts", 0, "phase", "Running", "nodeName", "ip-10-0-2-80.ec2.internal")));
        outNodes.add(createNode("pod:twindigital/simulation-service-914f-p83l1", "simulation-service-914f-p83l1", "POD", "DEGRADED",
                Map.of("cpuRequests", "300m", "cpuLimits", "800m", "memoryRequests", "384Mi", "memoryLimits", "768Mi", "restarts", 2, "phase", "Running", "nodeName", "ip-10-0-2-80.ec2.internal")));

        // Node > Pod Edges
        outEdges.add(createEdge("e:node-pod:1", "node:ip-10-0-1-50", "pod:twindigital/gateway-67f8b-x89k1", "HIERARCHY", "runs on", false));
        outEdges.add(createEdge("e:node-pod:2", "node:ip-10-0-1-50", "pod:twindigital/auth-service-789a-k2j34", "HIERARCHY", "runs on", false));
        outEdges.add(createEdge("e:node-pod:3", "node:ip-10-0-2-80", "pod:twindigital/topology-service-541c-m91a2", "HIERARCHY", "runs on", false));
        outEdges.add(createEdge("e:node-pod:4", "node:ip-10-0-2-80", "pod:twindigital/simulation-service-914f-p83l1", "HIERARCHY", "runs on", false));

        // Deployment > Pod Edges
        outEdges.add(createEdge("e:dep-pod:1", "deployment:twindigital/gateway", "pod:twindigital/gateway-67f8b-x89k1", "HIERARCHY", "manages", false));
        outEdges.add(createEdge("e:dep-pod:2", "deployment:twindigital/auth-service", "pod:twindigital/auth-service-789a-k2j34", "HIERARCHY", "manages", false));
        outEdges.add(createEdge("e:dep-pod:3", "deployment:twindigital/topology-service", "pod:twindigital/topology-service-541c-m91a2", "HIERARCHY", "manages", false));
        outEdges.add(createEdge("e:dep-pod:4", "deployment:twindigital/simulation-service", "pod:twindigital/simulation-service-914f-p83l1", "HIERARCHY", "manages", false));

        // Service > Deployment Edges
        outEdges.add(createEdge("e:svc-dep:1", "service:twindigital/gateway", "deployment:twindigital/gateway", "HIERARCHY", "routes to", false));
        outEdges.add(createEdge("e:svc-dep:2", "service:twindigital/auth-service", "deployment:twindigital/auth-service", "HIERARCHY", "routes to", false));
        outEdges.add(createEdge("e:svc-dep:3", "service:twindigital/topology-service", "deployment:twindigital/topology-service", "HIERARCHY", "routes to", false));
        outEdges.add(createEdge("e:svc-dep:4", "service:twindigital/simulation-service", "deployment:twindigital/simulation-service", "HIERARCHY", "routes to", false));

        // Ingress -> Gateway
        outEdges.add(createEdge("e:ing-gw", "ingress:gateway-alb", "service:twindigital/gateway", "INGRESS", "routes traffic", true));

        // Service-to-Service Dependencies
        outEdges.add(createEdge("e:gw-auth", "service:twindigital/gateway", "service:twindigital/auth-service", "DEPENDENCY", "verifies JWT", true));
        outEdges.add(createEdge("e:gw-topo", "service:twindigital/gateway", "service:twindigital/topology-service", "DEPENDENCY", "reads topology", true));
        outEdges.add(createEdge("e:gw-sim", "service:twindigital/gateway", "service:twindigital/simulation-service", "DEPENDENCY", "runs simulations", true));
        outEdges.add(createEdge("e:sim-topo", "service:twindigital/simulation-service", "service:twindigital/topology-service", "DEPENDENCY", "queries graph", true));
        outEdges.add(createEdge("e:sim-cost", "service:twindigital/simulation-service", "service:twindigital/cost-risk-service", "DEPENDENCY", "computes cost", true));
        outEdges.add(createEdge("e:topo-sync", "service:twindigital/topology-service", "service:twindigital/cluster-sync-service", "DEPENDENCY", "consumes snapshot", true));
    }

    private TopologyNode createNode(String id, String name, String type, String health, Map<String, Object> details) {
        return TopologyNode.builder()
                .id(id)
                .name(name)
                .type(type)
                .health(health)
                .healthColor(getHealthColor(health))
                .details(new HashMap<>(details))
                .labels(Map.of("app.kubernetes.io/part-of", "twindigital"))
                .build();
    }

    private TopologyEdge createEdge(String id, String source, String target, String type, String label, boolean animated) {
        return TopologyEdge.builder()
                .id(id)
                .source(source)
                .target(target)
                .type(type)
                .label(label)
                .animated(animated)
                .build();
    }

    private boolean matchSelector(Map<String, String> selector, Map<String, String> labels) {
        if (selector == null || selector.isEmpty() || labels == null) return false;
        for (Map.Entry<String, String> entry : selector.entrySet()) {
            if (!entry.getValue().equals(labels.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private ClusterHealthSummary computeClusterHealth(List<TopologyNode> nodes) {
        int healthy = 0, degraded = 0, critical = 0;
        for (TopologyNode n : nodes) {
            if ("CRITICAL".equalsIgnoreCase(n.getHealth())) critical++;
            else if ("DEGRADED".equalsIgnoreCase(n.getHealth())) degraded++;
            else healthy++;
        }
        String overall = critical > 0 ? "CRITICAL" : (degraded > 0 ? "DEGRADED" : "HEALTHY");
        return ClusterHealthSummary.builder()
                .healthyCount(healthy)
                .degradedCount(degraded)
                .criticalCount(critical)
                .totalCount(nodes.size())
                .overallStatus(overall)
                .build();
    }

    private StalenessInfo computeStaleness(String clusterName) {
        Optional<ClusterSyncStateEntity> state = syncStateRepository.findByClusterName(clusterName);
        if (state.isPresent()) {
            ClusterSyncStateEntity s = state.get();
            boolean isStale = !s.isConnected() || s.getLagSeconds() > 120 || !"HEALTHY".equalsIgnoreCase(s.getStatus());
            return StalenessInfo.builder()
                    .isStale(isStale)
                    .lagSeconds(s.getLagSeconds())
                    .lastSyncedAt(s.getLastEventAt().toString())
                    .syncStatus(s.getStatus())
                    .build();
        }
        // Fallback default
        return StalenessInfo.builder()
                .isStale(false)
                .lagSeconds(3)
                .lastSyncedAt(Instant.now().toString())
                .syncStatus("HEALTHY")
                .build();
    }

    private String getHealthColor(String health) {
        if ("CRITICAL".equalsIgnoreCase(health)) return "#ef4444"; // Red
        if ("DEGRADED".equalsIgnoreCase(health)) return "#f59e0b"; // Amber
        return "#10b981"; // Green
    }
}
