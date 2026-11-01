package com.twindigital.costrisk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class CostRiskServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CostRiskServiceApplication.class, args);
    }
}
