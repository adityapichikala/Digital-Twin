package com.twindigital.simulation.check;

import com.twindigital.simulation.context.SimulationContext;
import com.twindigital.simulation.context.SimulationContext.BlastRadiusInfo;
import com.twindigital.simulation.model.SimulationModels.ChangeSet;
import com.twindigital.simulation.model.SimulationModels.FindingSeverity;
import com.twindigital.simulation.model.SimulationModels.SimulationFinding;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BlastRadiusCheck implements SimulationCheck {

    @Override
    public String getName() {
        return "BlastRadius";
    }

    @Override
    public List<SimulationFinding> evaluate(SimulationContext context) {
        List<SimulationFinding> findings = new ArrayList<>();
        ChangeSet cs = context.getChangeSet();
        if (cs == null || cs.getTarget() == null || cs.getTarget().isBlank()) {
            return findings;
        }

        String target = cs.getTarget().trim().toLowerCase(Locale.ROOT);

        // 1. Resolve blast radius using context provider or heuristic fallback
        BlastRadiusInfo info = null;
        if (context.getBlastRadiusResolver() != null) {
            try {
                info = context.getBlastRadiusResolver().apply(target);
            } catch (Exception ignored) {
            }
        }

        // Heuristic fallback if resolver is absent or returned null
        if (info == null) {
            info = inferDefaultBlastRadius(target);
        }

        List<String> upstream = info.getUpstreamCallers();
        List<String> downstream = info.getDownstreamDependencies();
        int totalImpacted = info.getTotalImpacted();

        // 2. Evaluate impact severity and generate findings
        if (target.contains("gateway")) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.CRITICAL)
                    .explanation("Target 'gateway' is the ingress edge. Disruptions will directly impact all external incoming traffic and all proxied microservices.")
                    .suggestedFix("Ensure multi-replica pod distribution across distinct availability zones and verify ingress readiness checks before modifying.")
                    .build());
        } else if (upstream.size() >= 3 || target.contains("auth")) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.HIGH)
                    .explanation(String.format("Broad blast radius: Target service '%s' has %d upstream caller(s) (%s) and %d downstream dependenc(ies) (%s). Total blast radius: %d service(s). Any scheduling disruption or restart will cascade through dependent services.",
                            cs.getTarget(), upstream.size(), String.join(", ", upstream),
                            downstream.size(), String.join(", ", downstream), totalImpacted))
                    .suggestedFix("Perform changes during a maintenance window with canary rollout and active health probes.")
                    .build());
        } else if (upstream.size() >= 1) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.MEDIUM)
                    .explanation(String.format("Target service '%s' has %d upstream caller(s) (%s) that rely on its availability.",
                            cs.getTarget(), upstream.size(), String.join(", ", upstream)))
                    .suggestedFix("Verify dependent services have client retry and circuit-breaker mechanisms configured.")
                    .build());
        } else if (totalImpacted > 0) {
            findings.add(SimulationFinding.builder()
                    .check(getName())
                    .severity(FindingSeverity.LOW)
                    .explanation(String.format("Target '%s' relies on %d downstream dependenc(ies) (%s).",
                            cs.getTarget(), downstream.size(), String.join(", ", downstream)))
                    .suggestedFix("Ensure downstream dependencies are healthy and reachable.")
                    .build());
        }

        return findings;
    }

    private BlastRadiusInfo inferDefaultBlastRadius(String target) {
        List<String> upstream = new ArrayList<>();
        List<String> downstream = new ArrayList<>();

        if (target.contains("auth")) {
            upstream.addAll(List.of("gateway", "topology-service", "simulation-service", "cluster-sync-service"));
            downstream.addAll(List.of("redis", "postgres"));
        } else if (target.contains("gateway")) {
            downstream.addAll(List.of("auth-service", "topology-service", "simulation-service", "cost-risk-service"));
        } else if (target.contains("topology")) {
            upstream.add("gateway");
            downstream.addAll(List.of("cluster-sync-service", "postgres"));
        } else if (target.contains("simulation")) {
            upstream.add("gateway");
            downstream.addAll(List.of("topology-service", "postgres"));
        } else if (target.contains("billing")) {
            upstream.add("gateway");
            downstream.addAll(List.of("auth-service", "postgres"));
        }

        return BlastRadiusInfo.builder()
                .target(target)
                .upstreamCallers(upstream)
                .downstreamDependencies(downstream)
                .totalImpacted(upstream.size() + downstream.size())
                .riskScore(upstream.size() >= 3 ? "HIGH" : (upstream.isEmpty() ? "LOW" : "MEDIUM"))
                .build();
    }
}
