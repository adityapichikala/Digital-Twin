package com.twindigital.clustersync.service;

import com.twindigital.clustersync.config.ClusterSyncProperties;
import com.twindigital.clustersync.metrics.ClusterSyncMetrics;
import com.twindigital.clustersync.model.mapper.KubernetesResourceMapper;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterSyncInformerManager implements SmartLifecycle {

    private final ApiClient apiClient;
    private final ClusterSyncProperties properties;
    private final KubernetesResourceMapper mapper;
    private final KubernetesResourcePersister persister;
    private final SyncHealthTracker healthTracker;
    private final ClusterSyncMetrics metrics;

    private SharedInformerFactory informerFactory;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Instant syncStartTime;
    private final AtomicBoolean initialSyncCompleted = new AtomicBoolean(false);
    private final List<SharedIndexInformer<?>> registeredInformers = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService supervisorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "k8s-informer-supervisor");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void start() {
        if (!properties.isAutoStart()) {
            log.info("Cluster sync auto-start disabled by configuration.");
            return;
        }

        log.info("Starting TwinDigital Kubernetes Informers for cluster: {}", properties.getClusterName());
        startInformers();
        scheduleSupervisor();
        running.set(true);
    }

    private synchronized void startInformers() {
        try {
            if (informerFactory != null) {
                try {
                    informerFactory.stopAllRegisteredInformers();
                } catch (Exception ignored) {}
            }

            syncStartTime = Instant.now();
            initialSyncCompleted.set(false);
            registeredInformers.clear();

            informerFactory = new SharedInformerFactory(apiClient);

            registerNodeInformer(informerFactory);
            registerPodInformer(informerFactory);
            registerServiceInformer(informerFactory);
            registerDeploymentInformer(informerFactory);
            registerHpaInformer(informerFactory);
            registerPdbInformer(informerFactory);

            // Start all registered informers asynchronously
            informerFactory.startAllRegisteredInformers();
            healthTracker.markConnected();
            log.info("All 6 Kubernetes informers registered and started successfully");

        } catch (Exception e) {
            log.error("Failed to start Kubernetes informers: {}. Scheduling reconnect with backoff.", e.getMessage());
            healthTracker.markDisconnected(e.getMessage());
            triggerBackoffReconnect();
        }
    }

    private void scheduleSupervisor() {
        // Runs every 10 seconds to monitor health, calculate lag, and detect disconnections
        supervisorExecutor.scheduleWithFixedDelay(this::monitorHealthAndConnectivity, 10, 10, TimeUnit.SECONDS);
    }

    private void monitorHealthAndConnectivity() {
        try {
            if (!running.get()) {
                return;
            }

            // Quick connectivity probe against API server
            CoreV1Api coreApi = new CoreV1Api(apiClient);
            coreApi.getAPIResources();
            healthTracker.recordHeartbeat();

            // Check if informers have synced
            if (informerFactory != null) {
                healthTracker.calculateLagSeconds();
            }

            // After initial sync, delete rows with last_synced_at older than sync start
            if (!initialSyncCompleted.get() && areAllInformersSynced()) {
                initialSyncCompleted.set(true);
                log.info("Initial sync completed for cluster {}. Pruning resources older than {}",
                        properties.getClusterName(), syncStartTime);
                persister.deleteStaleResources(properties.getClusterName(), syncStartTime);
            }
        } catch (Exception e) {
            log.warn("Cluster watcher health probe failed: {}. Connection dropped.", e.getMessage());
            healthTracker.markDisconnected("API Server connection error: " + e.getMessage());
            triggerBackoffReconnect();
        } finally {
            if (running.get()) {
                try {
                    healthTracker.persistSyncState();
                } catch (Exception ex) {
                    log.error("Failed to persist sync state to td_sync_state: {}", ex.getMessage());
                }
            }
        }
    }

    public boolean areAllInformersSynced() {
        if (registeredInformers.isEmpty()) {
            return false;
        }
        return registeredInformers.stream().allMatch(SharedIndexInformer::hasSynced);
    }

    private void triggerBackoffReconnect() {
        int attempt = healthTracker.incrementReconnectAttempts();
        long delaySeconds = healthTracker.computeBackoffDelaySeconds(attempt);
        log.info("Attempting Kubernetes informer reconnect #{} in {} seconds...", attempt, delaySeconds);

        supervisorExecutor.schedule(() -> {
            log.info("Executing reconnect attempt #{}...", attempt);
            startInformers();
        }, delaySeconds, TimeUnit.SECONDS);
    }

    // ── 1. Node Informer ───────────────────────────────────────────────────────────
    private void registerNodeInformer(SharedInformerFactory factory) {
        GenericKubernetesApi<V1Node, V1NodeList> nodeApi =
                new GenericKubernetesApi<>(V1Node.class, V1NodeList.class, "", "v1", "nodes", apiClient);

        SharedIndexInformer<V1Node> informer = factory.sharedIndexInformerFor(nodeApi, V1Node.class, 0L);
        registeredInformers.add(informer);
        informer.addEventHandler(new ResourceEventHandler<V1Node>() {
            @Override
            public void onAdd(V1Node node) {
                persister.persistNode(mapper.toNodeEntity(properties.getClusterName(), node));
                metrics.recordEvent("node", "ADDED");
                healthTracker.recordEvent("nodes");
                healthTracker.markResourceSynced("nodes", true);
            }

            @Override
            public void onUpdate(V1Node oldNode, V1Node newNode) {
                persister.persistNode(mapper.toNodeEntity(properties.getClusterName(), newNode));
                metrics.recordEvent("node", "UPDATED");
                healthTracker.recordEvent("nodes");
            }

            @Override
            public void onDelete(V1Node node, boolean deletedFinalStateUnknown) {
                if (node != null && node.getMetadata() != null) {
                    persister.deleteNode(properties.getClusterName(), node.getMetadata().getName());
                    metrics.recordEvent("node", "DELETED");
                    healthTracker.recordEvent("nodes");
                }
            }
        });
    }

    // ── 2. Pod Informer ────────────────────────────────────────────────────────────
    private void registerPodInformer(SharedInformerFactory factory) {
        GenericKubernetesApi<V1Pod, V1PodList> podApi =
                new GenericKubernetesApi<>(V1Pod.class, V1PodList.class, "", "v1", "pods", apiClient);

        SharedIndexInformer<V1Pod> informer = factory.sharedIndexInformerFor(podApi, V1Pod.class, 0L);
        registeredInformers.add(informer);
        informer.addEventHandler(new ResourceEventHandler<V1Pod>() {
            @Override
            public void onAdd(V1Pod pod) {
                persister.persistPod(mapper.toPodEntity(properties.getClusterName(), pod));
                metrics.recordEvent("pod", "ADDED");
                healthTracker.recordEvent("pods");
                healthTracker.markResourceSynced("pods", true);
            }

            @Override
            public void onUpdate(V1Pod oldPod, V1Pod newPod) {
                persister.persistPod(mapper.toPodEntity(properties.getClusterName(), newPod));
                metrics.recordEvent("pod", "UPDATED");
                healthTracker.recordEvent("pods");
            }

            @Override
            public void onDelete(V1Pod pod, boolean deletedFinalStateUnknown) {
                if (pod != null && pod.getMetadata() != null) {
                    String ns = pod.getMetadata().getNamespace() != null ? pod.getMetadata().getNamespace() : "default";
                    persister.deletePod(properties.getClusterName(), ns, pod.getMetadata().getName());
                    metrics.recordEvent("pod", "DELETED");
                    healthTracker.recordEvent("pods");
                }
            }
        });
    }

    // ── 3. Service Informer ────────────────────────────────────────────────────────
    private void registerServiceInformer(SharedInformerFactory factory) {
        GenericKubernetesApi<V1Service, V1ServiceList> svcApi =
                new GenericKubernetesApi<>(V1Service.class, V1ServiceList.class, "", "v1", "services", apiClient);

        SharedIndexInformer<V1Service> informer = factory.sharedIndexInformerFor(svcApi, V1Service.class, 0L);
        registeredInformers.add(informer);
        informer.addEventHandler(new ResourceEventHandler<V1Service>() {
            @Override
            public void onAdd(V1Service svc) {
                persister.persistService(mapper.toServiceEntity(properties.getClusterName(), svc));
                metrics.recordEvent("service", "ADDED");
                healthTracker.recordEvent("services");
                healthTracker.markResourceSynced("services", true);
            }

            @Override
            public void onUpdate(V1Service oldSvc, V1Service newSvc) {
                persister.persistService(mapper.toServiceEntity(properties.getClusterName(), newSvc));
                metrics.recordEvent("service", "UPDATED");
                healthTracker.recordEvent("services");
            }

            @Override
            public void onDelete(V1Service svc, boolean deletedFinalStateUnknown) {
                if (svc != null && svc.getMetadata() != null) {
                    String ns = svc.getMetadata().getNamespace() != null ? svc.getMetadata().getNamespace() : "default";
                    persister.deleteService(properties.getClusterName(), ns, svc.getMetadata().getName());
                    metrics.recordEvent("service", "DELETED");
                    healthTracker.recordEvent("services");
                }
            }
        });
    }

    // ── 4. Deployment Informer ─────────────────────────────────────────────────────
    private void registerDeploymentInformer(SharedInformerFactory factory) {
        GenericKubernetesApi<V1Deployment, V1DeploymentList> depApi =
                new GenericKubernetesApi<>(V1Deployment.class, V1DeploymentList.class, "apps", "v1", "deployments", apiClient);

        SharedIndexInformer<V1Deployment> informer = factory.sharedIndexInformerFor(depApi, V1Deployment.class, 0L);
        registeredInformers.add(informer);
        informer.addEventHandler(new ResourceEventHandler<V1Deployment>() {
            @Override
            public void onAdd(V1Deployment dep) {
                persister.persistDeployment(mapper.toDeploymentEntity(properties.getClusterName(), dep));
                metrics.recordEvent("deployment", "ADDED");
                healthTracker.recordEvent("deployments");
                healthTracker.markResourceSynced("deployments", true);
            }

            @Override
            public void onUpdate(V1Deployment oldDep, V1Deployment newDep) {
                persister.persistDeployment(mapper.toDeploymentEntity(properties.getClusterName(), newDep));
                metrics.recordEvent("deployment", "UPDATED");
                healthTracker.recordEvent("deployments");
            }

            @Override
            public void onDelete(V1Deployment dep, boolean deletedFinalStateUnknown) {
                if (dep != null && dep.getMetadata() != null) {
                    String ns = dep.getMetadata().getNamespace() != null ? dep.getMetadata().getNamespace() : "default";
                    persister.deleteDeployment(properties.getClusterName(), ns, dep.getMetadata().getName());
                    metrics.recordEvent("deployment", "DELETED");
                    healthTracker.recordEvent("deployments");
                }
            }
        });
    }

    // ── 5. HPA Informer ────────────────────────────────────────────────────────────
    private void registerHpaInformer(SharedInformerFactory factory) {
        GenericKubernetesApi<V2HorizontalPodAutoscaler, V2HorizontalPodAutoscalerList> hpaApi =
                new GenericKubernetesApi<>(V2HorizontalPodAutoscaler.class, V2HorizontalPodAutoscalerList.class,
                        "autoscaling", "v2", "horizontalpodautoscalers", apiClient);

        SharedIndexInformer<V2HorizontalPodAutoscaler> informer =
                factory.sharedIndexInformerFor(hpaApi, V2HorizontalPodAutoscaler.class, 0L);
        registeredInformers.add(informer);

        informer.addEventHandler(new ResourceEventHandler<V2HorizontalPodAutoscaler>() {
            @Override
            public void onAdd(V2HorizontalPodAutoscaler hpa) {
                persister.persistHpa(mapper.toHpaEntity(properties.getClusterName(), hpa));
                metrics.recordEvent("hpa", "ADDED");
                healthTracker.recordEvent("hpas");
                healthTracker.markResourceSynced("hpas", true);
            }

            @Override
            public void onUpdate(V2HorizontalPodAutoscaler oldHpa, V2HorizontalPodAutoscaler newHpa) {
                persister.persistHpa(mapper.toHpaEntity(properties.getClusterName(), newHpa));
                metrics.recordEvent("hpa", "UPDATED");
                healthTracker.recordEvent("hpas");
            }

            @Override
            public void onDelete(V2HorizontalPodAutoscaler hpa, boolean deletedFinalStateUnknown) {
                if (hpa != null && hpa.getMetadata() != null) {
                    String ns = hpa.getMetadata().getNamespace() != null ? hpa.getMetadata().getNamespace() : "default";
                    persister.deleteHpa(properties.getClusterName(), ns, hpa.getMetadata().getName());
                    metrics.recordEvent("hpa", "DELETED");
                    healthTracker.recordEvent("hpas");
                }
            }
        });
    }

    // ── 6. PDB Informer ────────────────────────────────────────────────────────────
    private void registerPdbInformer(SharedInformerFactory factory) {
        GenericKubernetesApi<V1PodDisruptionBudget, V1PodDisruptionBudgetList> pdbApi =
                new GenericKubernetesApi<>(V1PodDisruptionBudget.class, V1PodDisruptionBudgetList.class,
                        "policy", "v1", "poddisruptionbudgets", apiClient);

        SharedIndexInformer<V1PodDisruptionBudget> informer =
                factory.sharedIndexInformerFor(pdbApi, V1PodDisruptionBudget.class, 0L);
        registeredInformers.add(informer);

        informer.addEventHandler(new ResourceEventHandler<V1PodDisruptionBudget>() {
            @Override
            public void onAdd(V1PodDisruptionBudget pdb) {
                persister.persistPdb(mapper.toPdbEntity(properties.getClusterName(), pdb));
                metrics.recordEvent("pdb", "ADDED");
                healthTracker.recordEvent("pdbs");
                healthTracker.markResourceSynced("pdbs", true);
            }

            @Override
            public void onUpdate(V1PodDisruptionBudget oldPdb, V1PodDisruptionBudget newPdb) {
                persister.persistPdb(mapper.toPdbEntity(properties.getClusterName(), newPdb));
                metrics.recordEvent("pdb", "UPDATED");
                healthTracker.recordEvent("pdbs");
            }

            @Override
            public void onDelete(V1PodDisruptionBudget pdb, boolean deletedFinalStateUnknown) {
                if (pdb != null && pdb.getMetadata() != null) {
                    String ns = pdb.getMetadata().getNamespace() != null ? pdb.getMetadata().getNamespace() : "default";
                    persister.deletePdb(properties.getClusterName(), ns, pdb.getMetadata().getName());
                    metrics.recordEvent("pdb", "DELETED");
                    healthTracker.recordEvent("pdbs");
                }
            }
        });
    }

    @Override
    public void stop() {
        running.set(false);
        supervisorExecutor.shutdownNow();
        if (informerFactory != null) {
            informerFactory.stopAllRegisteredInformers();
            log.info("Stopped all registered Kubernetes informers");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
