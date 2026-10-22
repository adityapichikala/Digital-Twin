package com.twindigital.clustersync.repository;

import com.twindigital.clustersync.model.entity.DeploymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeploymentRepository extends JpaRepository<DeploymentEntity, UUID> {
    Optional<DeploymentEntity> findByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    List<DeploymentEntity> findByClusterName(String clusterName);
    long deleteByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    long countByClusterName(String clusterName);
    long deleteByClusterNameAndLastSyncedAtBefore(String clusterName, Instant timestamp);
}
