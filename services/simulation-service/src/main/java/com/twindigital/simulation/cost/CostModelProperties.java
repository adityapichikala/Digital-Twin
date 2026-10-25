package com.twindigital.simulation.cost;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configurable pricing model for Kubernetes compute resources.
 * Completely independent of AWS APIs.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cost.pricing")
public class CostModelProperties {

    /**
     * Hourly cost per vCPU (e.g. $0.0316 / vCPU-hour, based on standard cloud general-purpose pricing).
     */
    private double vcpuHourlyRate = 0.0316;

    /**
     * Hourly cost per GB of RAM (e.g. $0.0042 / GB-hour).
     */
    private double memoryGbHourlyRate = 0.0042;

    /**
     * Total operational hours per month (730 hours average).
     */
    private double monthlyHours = 730.0;

    /**
     * Safety buffer multiplier for right-sizing (e.g., 1.25 = 25% safety headroom).
     */
    private double safetyHeadroom = 1.20;

    /**
     * Minimum allocatable CPU floor in millicores (50m).
     */
    private long minCpuMillicores = 50;

    /**
     * Minimum allocatable Memory floor in MiB (64Mi).
     */
    private long minMemoryMib = 64;
}
