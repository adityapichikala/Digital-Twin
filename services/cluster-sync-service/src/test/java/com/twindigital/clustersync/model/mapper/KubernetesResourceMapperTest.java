package com.twindigital.clustersync.model.mapper;

import com.twindigital.clustersync.model.entity.*;
import com.twindigital.clustersync.util.KubeQuantityParser;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KubernetesResourceMapperTest {

    private KubernetesResourceMapper mapper;
    private final String clusterName = "test-cluster";

    @BeforeEach
    void setUp() {
        mapper = new KubernetesResourceMapper();
    }

    @Test
    @DisplayName("Should map V1Node with capacity, allocatable, ready condition and labels")
    void shouldMapNodeEntity() {
        V1Node node = new V1Node()
                .metadata(new V1ObjectMeta()
                        .name("ip-10-0-1-50.ec2.internal")
                        .labels(Map.of("node.kubernetes.io/instance-type", "t3.medium", "topology.kubernetes.io/zone", "ap-south-1a")))
                .status(new V1NodeStatus()
                        .capacity(Map.of("cpu", new Quantity("2"), "memory", new Quantity("4194304Ki")))
                        .allocatable(Map.of("cpu", new Quantity("1930m"), "memory", new Quantity("3800000Ki")))
                        .conditions(List.of(
                                new V1NodeCondition().type("Ready").status("True").reason("KubeletReady"),
                                new V1NodeCondition().type("MemoryPressure").status("False")
                        )));

        NodeEntity entity = mapper.toNodeEntity(clusterName, node);

        assertNotNull(entity);
        assertEquals(clusterName, entity.getClusterName());
        assertEquals("ip-10-0-1-50.ec2.internal", entity.getName());
        assertEquals("1930m", entity.getAllocatableCpu());
        assertEquals("3800000Ki", entity.getAllocatableMemory());
        assertEquals("2000m", entity.getCapacityCpu());
        assertEquals("4096Mi", entity.getCapacityMemory());
        assertEquals(1930L, KubeQuantityParser.parseCpuMillis(entity.getAllocatableCpu()));
        assertEquals(2000L, KubeQuantityParser.parseCpuMillis(entity.getCapacityCpu()));
        assertEquals(3800000L * 1024L, KubeQuantityParser.parseMemoryBytes(entity.getAllocatableMemory()));
        assertEquals(4096L * 1024L * 1024L, KubeQuantityParser.parseMemoryBytes(entity.getCapacityMemory()));
        assertTrue(entity.isReady());
        assertEquals("Ready", entity.getStatus());
        assertEquals("t3.medium", entity.getLabels().get("node.kubernetes.io/instance-type"));
        assertEquals("True", entity.getConditions().get("Ready"));
        assertNotNull(entity.getLastSyncedAt());
    }

    @Test
    @DisplayName("Should map V1Pod with containers, requests, limits, phase, restarts and owner")
    void shouldMapPodEntity() {
        V1Container container1 = new V1Container()
                .name("app")
                .resources(new V1ResourceRequirements()
                        .requests(Map.of("cpu", new Quantity("250m"), "memory", new Quantity("536870912"))) // 512Mi
                        .limits(Map.of("cpu", new Quantity("500m"), "memory", new Quantity("1073741824")))); // 1024Mi

        V1Pod pod = new V1Pod()
                .metadata(new V1ObjectMeta()
                        .name("simulation-service-674b9-abcde")
                        .namespace("twindigital")
                        .ownerReferences(List.of(new V1OwnerReference().kind("ReplicaSet").name("simulation-service-674b9")))
                        .labels(Map.of("app.kubernetes.io/name", "simulation-service")))
                .spec(new V1PodSpec()
                        .nodeName("ip-10-0-1-50.ec2.internal")
                        .containers(List.of(container1)))
                .status(new V1PodStatus()
                        .phase("Running")
                        .containerStatuses(List.of(
                                new V1ContainerStatus().name("app").restartCount(2)
                        )));

        PodEntity entity = mapper.toPodEntity(clusterName, pod);

        assertNotNull(entity);
        assertEquals(clusterName, entity.getClusterName());
        assertEquals("twindigital", entity.getNamespace());
        assertEquals("simulation-service-674b9-abcde", entity.getName());
        assertEquals("ip-10-0-1-50.ec2.internal", entity.getNodeName());
        assertEquals("Deployment", entity.getOwnerKind());
        assertEquals("simulation-service", entity.getOwnerName());
        assertEquals("Running", entity.getPhase());
        assertEquals(2, entity.getRestarts());
        assertEquals("250m", entity.getCpuRequests());
        assertEquals("512Mi", entity.getMemoryRequests());
        assertEquals("500m", entity.getCpuLimits());
        assertEquals("1024Mi", entity.getMemoryLimits());
        assertEquals(250L, KubeQuantityParser.parseCpuMillis(entity.getCpuRequests()));
        assertEquals(536870912L, KubeQuantityParser.parseMemoryBytes(entity.getMemoryRequests()));
        assertEquals(500L, KubeQuantityParser.parseCpuMillis(entity.getCpuLimits()));
        assertEquals(1073741824L, KubeQuantityParser.parseMemoryBytes(entity.getMemoryLimits()));
        assertEquals("simulation-service", entity.getLabels().get("app.kubernetes.io/name"));
    }

    @Test
    @DisplayName("Should map V1Service with selector, ports, and type")
    void shouldMapServiceEntity() {
        V1Service service = new V1Service()
                .metadata(new V1ObjectMeta()
                        .name("gateway")
                        .namespace("twindigital")
                        .labels(Map.of("app", "gateway")))
                .spec(new V1ServiceSpec()
                        .type("ClusterIP")
                        .clusterIP("172.20.10.15")
                        .selector(Map.of("app", "gateway"))
                        .ports(List.of(
                                new V1ServicePort().name("http").port(3000).targetPort(new IntOrString(3000)).protocol("TCP")
                        )));

        ServiceEntity entity = mapper.toServiceEntity(clusterName, service);

        assertNotNull(entity);
        assertEquals("gateway", entity.getName());
        assertEquals("twindigital", entity.getNamespace());
        assertEquals("ClusterIP", entity.getServiceType());
        assertEquals("172.20.10.15", entity.getClusterIp());
        assertEquals("gateway", entity.getSelector().get("app"));
        assertEquals(1, entity.getPorts().size());
        assertEquals(3000, entity.getPorts().get(0).get("port"));
    }

    @Test
    @DisplayName("Should map V1Deployment with replica counts and selector")
    void shouldMapDeploymentEntity() {
        V1Deployment deployment = new V1Deployment()
                .metadata(new V1ObjectMeta()
                        .name("auth-service")
                        .namespace("twindigital")
                        .labels(Map.of("app", "auth-service"))
                        .annotations(Map.of("twindigital.io/depends-on", "postgres,redis")))
                .spec(new V1DeploymentSpec()
                        .replicas(3)
                        .selector(new V1LabelSelector().matchLabels(Map.of("app", "auth-service"))))
                .status(new V1DeploymentStatus()
                        .readyReplicas(3)
                        .availableReplicas(3)
                        .updatedReplicas(3));

        DeploymentEntity entity = mapper.toDeploymentEntity(clusterName, deployment);

        assertNotNull(entity);
        assertEquals("auth-service", entity.getName());
        assertEquals(3, entity.getReplicas());
        assertEquals(3, entity.getReadyReplicas());
        assertEquals(3, entity.getAvailableReplicas());
        assertEquals("auth-service", entity.getSelector().get("app"));
        assertNotNull(entity.getAnnotations());
        assertEquals("postgres,redis", entity.getAnnotations().get("twindigital.io/depends-on"));
    }

    @Test
    @DisplayName("Should map V2HorizontalPodAutoscaler with min/max replicas and CPU target")
    void shouldMapHpaEntity() {
        V2HorizontalPodAutoscaler hpa = new V2HorizontalPodAutoscaler()
                .metadata(new V1ObjectMeta()
                        .name("simulation-hpa")
                        .namespace("twindigital"))
                .spec(new V2HorizontalPodAutoscalerSpec()
                        .scaleTargetRef(new V2CrossVersionObjectReference().kind("Deployment").name("simulation-service"))
                        .minReplicas(2)
                        .maxReplicas(10)
                        .metrics(List.of(
                                new V2MetricSpec()
                                        .type("Resource")
                                        .resource(new V2ResourceMetricSource()
                                                .name("cpu")
                                                .target(new V2MetricTarget().type("Utilization").averageUtilization(75)))
                        )))
                .status(new V2HorizontalPodAutoscalerStatus()
                        .currentReplicas(4)
                        .desiredReplicas(5)
                        .currentMetrics(List.of(
                                new V2MetricStatus()
                                        .type("Resource")
                                        .resource(new V2ResourceMetricStatus()
                                                .name("cpu")
                                                .current(new V2MetricValueStatus().averageUtilization(82)))
                        )));

        HpaEntity entity = mapper.toHpaEntity(clusterName, hpa);

        assertNotNull(entity);
        assertEquals("simulation-hpa", entity.getName());
        assertEquals("Deployment", entity.getTargetKind());
        assertEquals("simulation-service", entity.getTargetName());
        assertEquals(2, entity.getMinReplicas());
        assertEquals(10, entity.getMaxReplicas());
        assertEquals(4, entity.getCurrentReplicas());
        assertEquals(5, entity.getDesiredReplicas());
        assertEquals(75, entity.getTargetCpuUtilization());
        assertEquals(82, entity.getCurrentCpuUtilization());
    }

    @Test
    @DisplayName("Should map V1PodDisruptionBudget with minAvailable and disruptionsAllowed")
    void shouldMapPdbEntity() {
        V1PodDisruptionBudget pdb = new V1PodDisruptionBudget()
                .metadata(new V1ObjectMeta()
                        .name("gateway-pdb")
                        .namespace("twindigital"))
                .spec(new V1PodDisruptionBudgetSpec()
                        .minAvailable(new IntOrString(1))
                        .selector(new V1LabelSelector().matchLabels(Map.of("app", "gateway"))))
                .status(new V1PodDisruptionBudgetStatus()
                        .currentHealthy(2)
                        .desiredHealthy(2)
                        .disruptionsAllowed(1)
                        .expectedPods(2));

        PdbEntity entity = mapper.toPdbEntity(clusterName, pdb);

        assertNotNull(entity);
        assertEquals("gateway-pdb", entity.getName());
        assertEquals("1", entity.getMinAvailable());
        assertEquals(2, entity.getCurrentHealthy());
        assertEquals(1, entity.getDisruptionsAllowed());
        assertEquals("gateway", entity.getSelector().get("app"));
    }

    @Test
    @DisplayName("Should resolve pod ownerName from ReplicaSet using pod-template-hash label")
    void shouldResolvePodOwnerNameWithHashLabel() {
        V1Pod pod = new V1Pod()
                .metadata(new V1ObjectMeta()
                        .name("billing-service-5c69784795-xy123")
                        .namespace("twindigital")
                        .ownerReferences(List.of(new V1OwnerReference().kind("ReplicaSet").name("billing-service-5c69784795")))
                        .labels(Map.of("app", "billing-service", "pod-template-hash", "5c69784795")));

        PodEntity entity = mapper.toPodEntity(clusterName, pod);
        assertNotNull(entity);
        assertEquals("Deployment", entity.getOwnerKind());
        assertEquals("billing-service", entity.getOwnerName());
    }

    @Test
    @DisplayName("Should round-trip CPU and Memory quantities through KubeQuantityParser")
    void shouldRoundTripQuantitiesThroughParser() {
        // CPU millicores round-trip
        String cpu1 = mapper.formatCpu(new BigDecimal("0.250"));
        assertEquals("250m", cpu1);
        assertEquals(250L, KubeQuantityParser.parseCpuMillis(cpu1));

        String cpu2 = mapper.formatCpu(new BigDecimal("2"));
        assertEquals("2000m", cpu2);
        assertEquals(2000L, KubeQuantityParser.parseCpuMillis(cpu2));

        String cpu3 = mapper.formatCpu(new Quantity("100m"));
        assertEquals("100m", cpu3);
        assertEquals(100L, KubeQuantityParser.parseCpuMillis(cpu3));

        // Memory Mi/Ki round-trip
        String mem1 = mapper.formatMemory(new BigDecimal(536870912L)); // 512Mi
        assertEquals("512Mi", mem1);
        assertEquals(536870912L, KubeQuantityParser.parseMemoryBytes(mem1));

        String mem2 = mapper.formatMemory(new BigDecimal(65536L)); // 64Ki
        assertEquals("64Ki", mem2);
        assertEquals(65536L, KubeQuantityParser.parseMemoryBytes(mem2));

        String mem3 = mapper.formatMemory(new Quantity("1024Mi"));
        assertEquals("1024Mi", mem3);
        assertEquals(1073741824L, KubeQuantityParser.parseMemoryBytes(mem3));
    }

    @Test
    @DisplayName("Should safely return null when input object is null")
    void shouldReturnNullForNullInput() {
        assertNull(mapper.toNodeEntity(clusterName, null));
        assertNull(mapper.toPodEntity(clusterName, null));
        assertNull(mapper.toServiceEntity(clusterName, null));
        assertNull(mapper.toDeploymentEntity(clusterName, null));
        assertNull(mapper.toHpaEntity(clusterName, null));
        assertNull(mapper.toPdbEntity(clusterName, null));
    }
}
