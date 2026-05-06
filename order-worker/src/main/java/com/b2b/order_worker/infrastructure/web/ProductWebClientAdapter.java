package com.b2b.order_worker.infrastructure.web;

import com.b2b.order_worker.application.ports.output.ProductPort;
import com.b2b.order_worker.domain.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class ProductWebClientAdapter implements ProductPort {

    private final WebClient webClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final io.github.resilience4j.retry.Retry retry;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;
    private final long ttlSeconds;

    public ProductWebClientAdapter(
            WebClient.Builder webClientBuilder,
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${app.products-api.url}") String baseUrl,
            @Value("${app.redis.ttl-seconds}") long ttlSeconds) {

        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.retry = retryRegistry.retry("external-api");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("external-api");
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Mono<Product> findById(String productId) {
        String cacheKey = "product:" + productId;
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(this::deserializeProduct)
                .switchIfEmpty(fetchAndCache(productId, cacheKey));
    }

    private Mono<Product> fetchAndCache(String productId, String cacheKey) {
        return webClient.get()
                .uri("/products/{id}", productId)
                .retrieve()
                .bodyToMono(Product.class)
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .flatMap(product -> redisTemplate.opsForValue()
                        .set(cacheKey, serialize(product), Duration.ofSeconds(ttlSeconds))
                        .thenReturn(product))
                .doOnNext(p -> log.debug("Fetched product {} from API", productId));
    }

    private Mono<Product> deserializeProduct(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, Product.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize cached product, fetching from API", e);
            return Mono.empty();
        }
    }

    private String serialize(Product product) {
        try {
            return objectMapper.writeValueAsString(product);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize product", e);
        }
    }
}
