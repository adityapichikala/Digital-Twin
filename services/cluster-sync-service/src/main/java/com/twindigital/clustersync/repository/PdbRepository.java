package com.twindigital.clustersync.repository;

import com.twindigital.clustersync.model.entity.PdbEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PdbRepository extends JpaRepository<PdbEntity, UUID> {
    Optional<PdbEntity> findByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    List<PdbEntity> findByClusterName(String clusterName);
    long deleteByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    long countByClusterName(String clusterName);
}
