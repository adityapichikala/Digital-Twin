package com.twindigital.clustersync.repository;

import com.twindigital.clustersync.model.entity.PodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PodRepository extends JpaRepository<PodEntity, UUID> {
    Optional<PodEntity> findByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    List<PodEntity> findByClusterName(String clusterName);
    List<PodEntity> findByClusterNameAndNamespace(String clusterName, String namespace);
    List<PodEntity> findByClusterNameAndNodeName(String clusterName, String nodeName);
    long deleteByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    long countByClusterName(String clusterName);
    long deleteByClusterNameAndLastSyncedAtBefore(String clusterName, Instant timestamp);
}
