package com.twindigital.topology.config;

import com.twindigital.topology.repository.TopologyRepositories;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackageClasses = TopologyRepositories.class,
        considerNestedRepositories = true
)
public class TopologyJpaConfig {
}
