package com.twindigital.clustersync.repository;

import com.twindigital.clustersync.model.entity.HpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HpaRepository extends JpaRepository<HpaEntity, UUID> {
    Optional<HpaEntity> findByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    List<HpaEntity> findByClusterName(String clusterName);
    long deleteByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    long countByClusterName(String clusterName);
    long deleteByClusterNameAndLastSyncedAtBefore(String clusterName, Instant timestamp);
}
