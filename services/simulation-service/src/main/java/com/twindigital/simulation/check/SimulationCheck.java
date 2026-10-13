package com.twindigital.simulation.check;

import com.twindigital.simulation.context.SimulationContext;
import com.twindigital.simulation.model.SimulationModels.SimulationFinding;

import java.util.List;

public interface SimulationCheck {
    /**
     * Unique identifier for the check (e.g. "SchedulingFit", "LimitSanity").
     */
    String getName();

    /**
     * Evaluates the proposed change set against the cluster context and returns findings.
     */
    List<SimulationFinding> evaluate(SimulationContext context);
}
