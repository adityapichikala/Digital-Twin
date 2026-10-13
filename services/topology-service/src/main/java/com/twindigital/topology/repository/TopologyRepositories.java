package com.twindigital.topology.repository;

import com.twindigital.topology.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClusterNodeRepository extends JpaRepository<ClusterNodeEntity, UUID> {
    List<ClusterNodeEntity> findByClusterName(String clusterName);
}

@Repository
public interface ClusterPodRepository extends JpaRepository<ClusterPodEntity, UUID> {
    List<ClusterPodEntity> findByClusterName(String clusterName);
    List<ClusterPodEntity> findByClusterNameAndNodeName(String clusterName, String nodeName);
}

@Repository
public interface ClusterDeploymentRepository extends JpaRepository<ClusterDeploymentEntity, UUID> {
    List<ClusterDeploymentEntity> findByClusterName(String clusterName);
}

@Repository
public interface ClusterServiceRepository extends JpaRepository<ClusterServiceEntity, UUID> {
    List<ClusterServiceEntity> findByClusterName(String clusterName);
    Optional<ClusterServiceEntity> findByClusterNameAndName(String clusterName, String name);
}

@Repository
public interface ClusterSyncStateRepository extends JpaRepository<ClusterSyncStateEntity, UUID> {
    Optional<ClusterSyncStateEntity> findByClusterName(String clusterName);
}

@Repository
public interface ServiceDependencyRepository extends JpaRepository<ServiceDependencyEntity, UUID> {
    List<ServiceDependencyEntity> findByClusterName(String clusterName);
    List<ServiceDependencyEntity> findByClusterNameAndSourceService(String clusterName, String sourceService);
    List<ServiceDependencyEntity> findByClusterNameAndTargetService(String clusterName, String targetService);
}
