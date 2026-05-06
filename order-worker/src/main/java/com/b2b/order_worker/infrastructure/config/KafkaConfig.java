package com.b2b.order_worker.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
