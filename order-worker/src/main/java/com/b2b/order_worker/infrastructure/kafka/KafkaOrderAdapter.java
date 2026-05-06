package com.b2b.order_worker.infrastructure.kafka;

import com.b2b.order_worker.application.ports.input.ProcessOrderUseCase;
import com.b2b.order_worker.domain.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOrderAdapter {

    private final ProcessOrderUseCase processOrderUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topics.orders}")
    public void handleOrder(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Received order from Kafka: key={}", record.key());
        deserialize(record.value())
                .flatMap(processOrderUseCase::process)
                .doFinally(signal -> ack.acknowledge())
                .onErrorResume(ex -> {
                    log.error("Unhandled error processing order key={}: {}", record.key(), ex.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    private Mono<Order> deserialize(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, Order.class));
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Invalid order JSON: " + e.getMessage(), e));
        }
    }
}
