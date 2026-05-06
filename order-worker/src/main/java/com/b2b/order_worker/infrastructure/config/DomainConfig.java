package com.b2b.order_worker.infrastructure.config;

import com.b2b.order_worker.domain.service.TaxCalculationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public TaxCalculationService taxCalculationService() {
        return new TaxCalculationService();
    }
}
