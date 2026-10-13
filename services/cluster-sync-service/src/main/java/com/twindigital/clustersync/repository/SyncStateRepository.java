package com.twindigital.clustersync.repository;

import com.twindigital.clustersync.model.entity.SyncStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncStateRepository extends JpaRepository<SyncStateEntity, UUID> {
    Optional<SyncStateEntity> findByClusterName(String clusterName);
}
