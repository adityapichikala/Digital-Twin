package com.twindigital.costrisk.config;

import com.twindigital.costrisk.repository.CostRiskRepositories;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackageClasses = CostRiskRepositories.class,
        considerNestedRepositories = true
)
public class CostRiskJpaConfig {
}
