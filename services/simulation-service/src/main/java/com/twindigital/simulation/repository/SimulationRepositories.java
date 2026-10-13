package com.twindigital.simulation.repository;

import com.twindigital.simulation.entity.SimulationEntities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SimulationRepositories {

    @Repository
    public interface SimulationAuditRepository extends JpaRepository<SimulationAuditEntity, UUID> {
        List<SimulationAuditEntity> findByClusterNameOrderByExecutedAtDesc(String clusterName);
        List<SimulationAuditEntity> findByUserIdOrderByExecutedAtDesc(String userId);
    }

    @Repository
    public interface ClusterNodeRepository extends JpaRepository<ClusterNodeEntity, UUID> {
        List<ClusterNodeEntity> findByClusterName(String clusterName);
        Optional<ClusterNodeEntity> findByClusterNameAndName(String clusterName, String name);
    }

    @Repository
    public interface ClusterPodRepository extends JpaRepository<ClusterPodEntity, UUID> {
        List<ClusterPodEntity> findByClusterName(String clusterName);
        List<ClusterPodEntity> findByClusterNameAndNodeName(String clusterName, String nodeName);
        List<ClusterPodEntity> findByClusterNameAndNamespace(String clusterName, String namespace);
    }

    @Repository
    public interface ClusterDeploymentRepository extends JpaRepository<ClusterDeploymentEntity, UUID> {
        List<ClusterDeploymentEntity> findByClusterName(String clusterName);
        Optional<ClusterDeploymentEntity> findByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    }

    @Repository
    public interface ClusterHpaRepository extends JpaRepository<ClusterHpaEntity, UUID> {
        List<ClusterHpaEntity> findByClusterName(String clusterName);
        List<ClusterHpaEntity> findByClusterNameAndNamespace(String clusterName, String namespace);
    }

    @Repository
    public interface ClusterPdbRepository extends JpaRepository<ClusterPdbEntity, UUID> {
        List<ClusterPdbEntity> findByClusterName(String clusterName);
        List<ClusterPdbEntity> findByClusterNameAndNamespace(String clusterName, String namespace);
    }

    @Repository
    public interface ClusterServiceRepository extends JpaRepository<ClusterServiceEntity, UUID> {
        List<ClusterServiceEntity> findByClusterName(String clusterName);
    }
}
