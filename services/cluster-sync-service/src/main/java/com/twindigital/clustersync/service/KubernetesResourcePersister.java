package com.twindigital.clustersync.service;

import com.twindigital.clustersync.model.entity.*;
import com.twindigital.clustersync.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class KubernetesResourcePersister {

    private final NodeRepository nodeRepository;
    private final PodRepository podRepository;
    private final ServiceRepository serviceRepository;
    private final DeploymentRepository deploymentRepository;
    private final HpaRepository hpaRepository;
    private final PdbRepository pdbRepository;
    private final SyncStateRepository syncStateRepository;

    @Transactional
    public void persistNode(NodeEntity node) {
        if (node == null) return;
        nodeRepository.findByClusterNameAndName(node.getClusterName(), node.getName())
                .ifPresentOrElse(existing -> {
                    existing.setAllocatableCpu(node.getAllocatableCpu());
                    existing.setAllocatableMemory(node.getAllocatableMemory());
                    existing.setCapacityCpu(node.getCapacityCpu());
                    existing.setCapacityMemory(node.getCapacityMemory());
                    existing.setReady(node.isReady());
                    existing.setStatus(node.getStatus());
                    existing.setLabels(node.getLabels());
                    existing.setConditions(node.getConditions());
                    existing.setLastSyncedAt(Instant.now());
                    nodeRepository.save(existing);
                    log.debug("Updated node snapshot: {}", node.getName());
                }, () -> {
                    node.setLastSyncedAt(Instant.now());
                    nodeRepository.save(node);
                    log.debug("Inserted node snapshot: {}", node.getName());
                });
    }

    @Transactional
    public void deleteNode(String clusterName, String name) {
        long deleted = nodeRepository.deleteByClusterNameAndName(clusterName, name);
        log.info("Deleted node from DB snapshot: {} (rows affected: {})", name, deleted);
    }

    @Transactional
    public void persistPod(PodEntity pod) {
        if (pod == null) return;
        podRepository.findByClusterNameAndNamespaceAndName(pod.getClusterName(), pod.getNamespace(), pod.getName())
                .ifPresentOrElse(existing -> {
                    existing.setNodeName(pod.getNodeName());
                    existing.setOwnerKind(pod.getOwnerKind());
                    existing.setOwnerName(pod.getOwnerName());
                    existing.setCpuRequests(pod.getCpuRequests());
                    existing.setMemoryRequests(pod.getMemoryRequests());
                    existing.setCpuLimits(pod.getCpuLimits());
                    existing.setMemoryLimits(pod.getMemoryLimits());
                    existing.setPhase(pod.getPhase());
                    existing.setRestarts(pod.getRestarts());
                    existing.setLabels(pod.getLabels());
                    existing.setLastSyncedAt(Instant.now());
                    podRepository.save(existing);
                    log.debug("Updated pod snapshot: {}/{}", pod.getNamespace(), pod.getName());
                }, () -> {
                    pod.setLastSyncedAt(Instant.now());
                    podRepository.save(pod);
                    log.debug("Inserted pod snapshot: {}/{}", pod.getNamespace(), pod.getName());
                });
    }

    @Transactional
    public void deletePod(String clusterName, String namespace, String name) {
        long deleted = podRepository.deleteByClusterNameAndNamespaceAndName(clusterName, namespace, name);
        log.info("Deleted pod from DB snapshot within seconds: {}/{} (rows: {})", namespace, name, deleted);
    }

    @Transactional
    public void persistService(ServiceEntity svc) {
        if (svc == null) return;
        serviceRepository.findByClusterNameAndNamespaceAndName(svc.getClusterName(), svc.getNamespace(), svc.getName())
                .ifPresentOrElse(existing -> {
                    existing.setServiceType(svc.getServiceType());
                    existing.setClusterIp(svc.getClusterIp());
                    existing.setSelector(svc.getSelector());
                    existing.setPorts(svc.getPorts());
                    existing.setLabels(svc.getLabels());
                    existing.setAnnotations(svc.getAnnotations());
                    existing.setLastSyncedAt(Instant.now());
                    serviceRepository.save(existing);
                    log.debug("Updated service snapshot: {}/{}", svc.getNamespace(), svc.getName());
                }, () -> {
                    svc.setLastSyncedAt(Instant.now());
                    serviceRepository.save(svc);
                    log.debug("Inserted service snapshot: {}/{}", svc.getNamespace(), svc.getName());
                });
    }

    @Transactional
    public void deleteService(String clusterName, String namespace, String name) {
        long deleted = serviceRepository.deleteByClusterNameAndNamespaceAndName(clusterName, namespace, name);
        log.info("Deleted service from DB snapshot: {}/{} (rows: {})", namespace, name, deleted);
    }

    @Transactional
    public void persistDeployment(DeploymentEntity dep) {
        if (dep == null) return;
        deploymentRepository.findByClusterNameAndNamespaceAndName(dep.getClusterName(), dep.getNamespace(), dep.getName())
                .ifPresentOrElse(existing -> {
                    existing.setReplicas(dep.getReplicas());
                    existing.setReadyReplicas(dep.getReadyReplicas());
                    existing.setAvailableReplicas(dep.getAvailableReplicas());
                    existing.setUpdatedReplicas(dep.getUpdatedReplicas());
                    existing.setSelector(dep.getSelector());
                    existing.setLabels(dep.getLabels());
                    existing.setAnnotations(dep.getAnnotations());
                    existing.setLastSyncedAt(Instant.now());
                    deploymentRepository.save(existing);
                    log.debug("Updated deployment snapshot: {}/{}", dep.getNamespace(), dep.getName());
                }, () -> {
                    dep.setLastSyncedAt(Instant.now());
                    deploymentRepository.save(dep);
                    log.debug("Inserted deployment snapshot: {}/{}", dep.getNamespace(), dep.getName());
                });
    }

    @Transactional
    public void deleteDeployment(String clusterName, String namespace, String name) {
        long deleted = deploymentRepository.deleteByClusterNameAndNamespaceAndName(clusterName, namespace, name);
        log.info("Deleted deployment from DB snapshot: {}/{} (rows: {})", namespace, name, deleted);
    }

    @Transactional
    public void persistHpa(HpaEntity hpa) {
        if (hpa == null) return;
        hpaRepository.findByClusterNameAndNamespaceAndName(hpa.getClusterName(), hpa.getNamespace(), hpa.getName())
                .ifPresentOrElse(existing -> {
                    existing.setTargetKind(hpa.getTargetKind());
                    existing.setTargetName(hpa.getTargetName());
                    existing.setMinReplicas(hpa.getMinReplicas());
                    existing.setMaxReplicas(hpa.getMaxReplicas());
                    existing.setCurrentReplicas(hpa.getCurrentReplicas());
                    existing.setDesiredReplicas(hpa.getDesiredReplicas());
                    existing.setCurrentCpuUtilization(hpa.getCurrentCpuUtilization());
                    existing.setTargetCpuUtilization(hpa.getTargetCpuUtilization());
                    existing.setLabels(hpa.getLabels());
                    existing.setLastSyncedAt(Instant.now());
                    hpaRepository.save(existing);
                    log.debug("Updated HPA snapshot: {}/{}", hpa.getNamespace(), hpa.getName());
                }, () -> {
                    hpa.setLastSyncedAt(Instant.now());
                    hpaRepository.save(hpa);
                    log.debug("Inserted HPA snapshot: {}/{}", hpa.getNamespace(), hpa.getName());
                });
    }

    @Transactional
    public void deleteHpa(String clusterName, String namespace, String name) {
        long deleted = hpaRepository.deleteByClusterNameAndNamespaceAndName(clusterName, namespace, name);
        log.info("Deleted HPA from DB snapshot: {}/{} (rows: {})", namespace, name, deleted);
    }

    @Transactional
    public void persistPdb(PdbEntity pdb) {
        if (pdb == null) return;
        pdbRepository.findByClusterNameAndNamespaceAndName(pdb.getClusterName(), pdb.getNamespace(), pdb.getName())
                .ifPresentOrElse(existing -> {
                    existing.setMinAvailable(pdb.getMinAvailable());
                    existing.setMaxUnavailable(pdb.getMaxUnavailable());
                    existing.setCurrentHealthy(pdb.getCurrentHealthy());
                    existing.setDesiredHealthy(pdb.getDesiredHealthy());
                    existing.setDisruptionsAllowed(pdb.getDisruptionsAllowed());
                    existing.setExpectedPods(pdb.getExpectedPods());
                    existing.setSelector(pdb.getSelector());
                    existing.setLabels(pdb.getLabels());
                    existing.setLastSyncedAt(Instant.now());
                    pdbRepository.save(existing);
                    log.debug("Updated PDB snapshot: {}/{}", pdb.getNamespace(), pdb.getName());
                }, () -> {
                    pdb.setLastSyncedAt(Instant.now());
                    pdbRepository.save(pdb);
                    log.debug("Inserted PDB snapshot: {}/{}", pdb.getNamespace(), pdb.getName());
                });
    }

    @Transactional
    public void deletePdb(String clusterName, String namespace, String name) {
        long deleted = pdbRepository.deleteByClusterNameAndNamespaceAndName(clusterName, namespace, name);
        log.info("Deleted PDB from DB snapshot: {}/{} (rows: {})", namespace, name, deleted);
    }

    @Transactional
    public void deleteStaleResources(String clusterName, Instant syncStart) {
        if (syncStart == null) return;
        long n = nodeRepository.deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        long p = podRepository.deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        long s = serviceRepository.deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        long d = deploymentRepository.deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        long h = hpaRepository.deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        long b = pdbRepository.deleteByClusterNameAndLastSyncedAtBefore(clusterName, syncStart);
        log.info("Pruned stale records older than sync start {} for cluster {}: nodes={}, pods={}, services={}, deployments={}, hpas={}, pdbs={}",
                syncStart, clusterName, n, p, s, d, h, b);
    }
}
