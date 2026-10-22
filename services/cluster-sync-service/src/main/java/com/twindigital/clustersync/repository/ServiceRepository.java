package com.twindigital.clustersync.repository;

import com.twindigital.clustersync.model.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {
    Optional<ServiceEntity> findByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    List<ServiceEntity> findByClusterName(String clusterName);
    long deleteByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    long countByClusterName(String clusterName);
    long deleteByClusterNameAndLastSyncedAtBefore(String clusterName, Instant timestamp);
}
