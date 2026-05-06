package com.b2b.order_worker.infrastructure.kafka;

import com.b2b.order_worker.application.ports.output.DeadLetterPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDltAdapter implements DeadLetterPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.dlt}")
    private String dltTopic;

    @Override
    public Mono<Void> send(String orderId, String cause, int attemptNumber, String originalPayload) {
        return buildPayload(orderId, cause, attemptNumber, originalPayload)
                .flatMap(json -> Mono.fromFuture(kafkaTemplate.send(dltTopic, orderId, json)))
                .doOnSuccess(result -> log.warn("Order {} sent to DLT", orderId))
                .onErrorResume(ex -> {
                    log.error("Failed to send order {} to DLT: {}", orderId, ex.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<String> buildPayload(String orderId, String cause, int attemptNumber, String originalPayload) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "timestamp", Instant.now().toString(),
                    "cause", cause != null ? cause : "unknown",
                    "attemptNumber", attemptNumber,
                    "originalPayload", originalPayload
            ));
            return Mono.just(json);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
