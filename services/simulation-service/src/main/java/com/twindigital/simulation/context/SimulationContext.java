package com.twindigital.simulation.context;

import com.twindigital.simulation.entity.SimulationEntities.*;
import com.twindigital.simulation.model.SimulationModels.ChangeSet;
import com.twindigital.simulation.util.KubeQuantityParser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.function.Function;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationContext {

    @Builder.Default
    private String clusterName = "twindigital-cluster";
    @Builder.Default
    private String user = "anonymous";
    private ChangeSet changeSet;

    @Builder.Default
    private List<ClusterNodeEntity> nodes = new ArrayList<>();
    @Builder.Default
    private List<ClusterPodEntity> pods = new ArrayList<>();
    @Builder.Default
    private List<ClusterDeploymentEntity> deployments = new ArrayList<>();
    @Builder.Default
    private List<ClusterHpaEntity> hpas = new ArrayList<>();
    @Builder.Default
    private List<ClusterPdbEntity> pdbs = new ArrayList<>();
    @Builder.Default
    private List<ClusterServiceEntity> services = new ArrayList<>();

    // Optional provider for blast radius
    private Function<String, BlastRadiusInfo> blastRadiusResolver;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlastRadiusInfo {
        private String target;
        @Builder.Default
        private List<String> upstreamCallers = new ArrayList<>();
        @Builder.Default
        private List<String> downstreamDependencies = new ArrayList<>();
        private int totalImpacted;
        private String riskScore;
    }

    public Optional<ClusterNodeEntity> findNode(String name) {
        if (name == null) return Optional.empty();
        return nodes.stream().filter(n -> n.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<ClusterDeploymentEntity> findDeployment(String name) {
        if (name == null) return Optional.empty();
        return deployments.stream().filter(d -> d.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<ClusterHpaEntity> findHpaForTarget(String target) {
        if (target == null) return Optional.empty();
        return hpas.stream().filter(h -> target.equalsIgnoreCase(h.getTargetName())).findFirst();
    }

    public Optional<ClusterPdbEntity> findPdbForTarget(String target) {
        if (target == null) return Optional.empty();
        return pdbs.stream().filter(p -> {
            if (p.getName().equalsIgnoreCase(target) || p.getName().startsWith(target)) return true;
            if (p.getSelector() != null && target.equalsIgnoreCase(p.getSelector().get("app"))) return true;
            return false;
        }).findFirst();
    }

    public List<ClusterPodEntity> getPodsOnNode(String nodeName) {
        if (nodeName == null) return Collections.emptyList();
        return pods.stream().filter(p -> nodeName.equalsIgnoreCase(p.getNodeName())).toList();
    }

    public List<ClusterPodEntity> getPodsForDeployment(String depName) {
        if (depName == null) return Collections.emptyList();
        return pods.stream().filter(p -> {
            if (depName.equalsIgnoreCase(p.getOwnerName())) return true;
            if (p.getName().startsWith(depName + "-")) return true;
            return false;
        }).toList();
    }

    public long getLargestNodeAllocatableCpu() {
        return nodes.stream()
                .filter(ClusterNodeEntity::isReady)
                .mapToLong(n -> KubeQuantityParser.parseCpuMillis(n.getAllocatableCpu()))
                .max()
                .orElse(0L);
    }

    public long getLargestNodeAllocatableMem() {
        return nodes.stream()
                .filter(ClusterNodeEntity::isReady)
                .mapToLong(n -> KubeQuantityParser.parseMemoryBytes(n.getAllocatableMemory()))
                .max()
                .orElse(0L);
    }

    public long getTotalClusterAllocatableCpu() {
        return nodes.stream()
                .filter(ClusterNodeEntity::isReady)
                .mapToLong(n -> KubeQuantityParser.parseCpuMillis(n.getAllocatableCpu()))
                .sum();
    }

    public long getTotalClusterAllocatableMem() {
        return nodes.stream()
                .filter(ClusterNodeEntity::isReady)
                .mapToLong(n -> KubeQuantityParser.parseMemoryBytes(n.getAllocatableMemory()))
                .sum();
    }
}
