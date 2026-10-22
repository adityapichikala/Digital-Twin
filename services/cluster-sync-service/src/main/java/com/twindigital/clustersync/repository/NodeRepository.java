package com.twindigital.clustersync.repository;

import com.twindigital.clustersync.model.entity.NodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NodeRepository extends JpaRepository<NodeEntity, UUID> {
    Optional<NodeEntity> findByClusterNameAndName(String clusterName, String name);
    List<NodeEntity> findByClusterName(String clusterName);
    long deleteByClusterNameAndName(String clusterName, String name);
    long countByClusterName(String clusterName);
    long deleteByClusterNameAndLastSyncedAtBefore(String clusterName, Instant timestamp);
}
