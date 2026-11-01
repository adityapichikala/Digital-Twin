package com.twindigital.simulation.config;

import com.twindigital.simulation.repository.SimulationRepositories;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackageClasses = SimulationRepositories.class,
        considerNestedRepositories = true
)
public class SimulationJpaConfig {
}
