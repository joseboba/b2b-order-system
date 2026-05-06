package com.b2b.order_worker.infrastructure.web;

import com.b2b.order_worker.application.ports.output.ClientPort;
import com.b2b.order_worker.domain.model.Client;
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
public class ClientWebClientAdapter implements ClientPort {

    private final WebClient webClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final io.github.resilience4j.retry.Retry retry;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;
    private final long ttlSeconds;

    public ClientWebClientAdapter(
            WebClient.Builder webClientBuilder,
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${app.clients-api.url}") String baseUrl,
            @Value("${app.redis.ttl-seconds}") long ttlSeconds) {

        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.retry = retryRegistry.retry("external-api");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("external-api");
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Mono<Client> findById(String clientId) {
        String cacheKey = "client:" + clientId;
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(this::deserializeClient)
                .switchIfEmpty(fetchAndCache(clientId, cacheKey));
    }

    private Mono<Client> fetchAndCache(String clientId, String cacheKey) {
        return webClient.get()
                .uri("/clients/{id}", clientId)
                .retrieve()
                .bodyToMono(Client.class)
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .flatMap(client -> redisTemplate.opsForValue()
                        .set(cacheKey, serialize(client), Duration.ofSeconds(ttlSeconds))
                        .thenReturn(client))
                .doOnNext(c -> log.debug("Fetched client {} from API", clientId));
    }

    private Mono<Client> deserializeClient(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, Client.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize cached client, fetching from API", e);
            return Mono.empty();
        }
    }

    private String serialize(Client client) {
        try {
            return objectMapper.writeValueAsString(client);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize client", e);
        }
    }
}
