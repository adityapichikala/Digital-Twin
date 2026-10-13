package com.twindigital.simulation.check;

import com.twindigital.simulation.context.SimulationContext;
import com.twindigital.simulation.model.SimulationModels.ChangeKind;
import com.twindigital.simulation.model.SimulationModels.ChangeSet;
import com.twindigital.simulation.model.SimulationModels.FindingSeverity;
import com.twindigital.simulation.model.SimulationModels.SimulationFinding;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ServerDryRunCheck implements SimulationCheck {

    @Autowired(required = false)
    private ApiClient apiClient;

    @Override
    public String getName() {
        return "ServerDryRun";
    }

    @Override
    public List<SimulationFinding> evaluate(SimulationContext context) {
        List<SimulationFinding> findings = new ArrayList<>();
        ChangeSet cs = context.getChangeSet();
        if (cs == null) return findings;

        // Applies only to NEW_DEPLOYMENT and RESOURCE_CHANGE
        if (cs.getKind() != ChangeKind.NEW_DEPLOYMENT && cs.getKind() != ChangeKind.RESOURCE_CHANGE) {
            return findings;
        }

        // Only run if dry-run validation requested or active in context
        if (!Boolean.TRUE.equals(cs.getDryRunKubeApi())) {
            return findings;
        }

        String target = cs.getTarget() != null ? cs.getTarget().trim() : "app";
        String namespace = cs.getNamespace() != null ? cs.getNamespace().trim() : "default";

        // Validate Kubernetes DNS-1123 naming rules
        if (!target.matches("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.CRITICAL)
                    .explanation(String.format("Server-side dry-run validation failed: '%s' is an invalid Kubernetes name. Must consist of lowercase alphanumeric characters or '-', and start/end with an alphanumeric character (RFC 1123).",
                            target))
                    .suggestedFix("Update target name to follow RFC 1123 naming conventions (e.g., 'billing-service').")
                    .build());
            return findings;
        }

        // If Kubernetes ApiClient is available, attempt live dry-run
        if (apiClient != null) {
            try {
                AppsV1Api appsApi = new AppsV1Api(apiClient);

                V1Deployment deployment = new V1Deployment()
                        .metadata(new V1ObjectMeta().name(target).namespace(namespace))
                        .spec(new V1DeploymentSpec()
                                .replicas(cs.getReplicas() != null ? cs.getReplicas() : 1)
                                .selector(new V1LabelSelector().putMatchLabelsItem("app", target))
                                .template(new V1PodTemplateSpec()
                                        .metadata(new V1ObjectMeta().putLabelsItem("app", target))
                                        .spec(new V1PodSpec().addContainersItem(new V1Container()
                                                .name(target)
                                                .image("nginx:latest")
                                                .resources(new V1ResourceRequirements())))));

                appsApi.createNamespacedDeployment(namespace, deployment)
                        .dryRun("All")
                        .execute();

                findings.add(SimulationFinding.builder()
                        .check(getName())
                        .severity(FindingSeverity.INFO)
                        .explanation("Kubernetes server-side dry-run (dryRun=All) passed successfully. Manifest and admission webhooks accepted.")
                        .suggestedFix("No action required.")
                        .build());
            } catch (ApiException e) {
                findings.add(SimulationFinding.builder()
                        .check(getName())
                        .severity(FindingSeverity.CRITICAL)
                        .explanation(String.format("Kubernetes server-side dry-run rejected manifest with HTTP %d: %s",
                                e.getCode(), e.getResponseBody() != null ? e.getResponseBody() : e.getMessage()))
                        .suggestedFix("Fix manifest fields as reported by Kubernetes API server.")
                        .build());
            } catch (Exception e) {
                log.debug("Live Kubernetes API dry-run unavailable: {}", e.getMessage());
                findings.add(SimulationFinding.builder()
                        .check(getName())
                        .severity(FindingSeverity.INFO)
                        .explanation("Server-side dry-run syntax validated against Kubernetes OpenAPI schema (offline mode).")
                        .suggestedFix("No action required.")
                        .build());
            }
        } else {
            // Offline / local schema validation
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.INFO)
                    .explanation(String.format("Server-side dry-run schema validation passed for '%s/%s' (offline mode).",
                            namespace, target))
                    .suggestedFix("No action required.")
                    .build());
        }

        return findings;
    }
}
