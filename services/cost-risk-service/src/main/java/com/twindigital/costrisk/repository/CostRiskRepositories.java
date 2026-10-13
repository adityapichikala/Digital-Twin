package com.twindigital.costrisk.repository;

import com.twindigital.costrisk.entity.CostRiskEntities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CostRiskRepositories {

    @Repository
    public interface InstancePricingRepository extends JpaRepository<InstancePricingEntity, UUID> {
        Optional<InstancePricingEntity> findByRegionAndInstanceType(String region, String instanceType);
        List<InstancePricingEntity> findByRegion(String region);
    }

    @Repository
    public interface CostAssessmentRepository extends JpaRepository<CostAssessmentEntity, UUID> {
        Optional<CostAssessmentEntity> findBySimulationId(UUID simulationId);
        List<CostAssessmentEntity> findByClusterNameOrderByComputedAtDesc(String clusterName);
    }

    @Repository
    public interface ClusterNodeRepository extends JpaRepository<ClusterNodeEntity, UUID> {
        List<ClusterNodeEntity> findByClusterName(String clusterName);
    }

    @Repository
    public interface ClusterPodRepository extends JpaRepository<ClusterPodEntity, UUID> {
        List<ClusterPodEntity> findByClusterName(String clusterName);
        List<ClusterPodEntity> findByClusterNameAndNamespace(String clusterName, String namespace);
    }

    @Repository
    public interface ClusterDeploymentRepository extends JpaRepository<ClusterDeploymentEntity, UUID> {
        List<ClusterDeploymentEntity> findByClusterName(String clusterName);
        Optional<ClusterDeploymentEntity> findByClusterNameAndNamespaceAndName(String clusterName, String namespace, String name);
    }
}
