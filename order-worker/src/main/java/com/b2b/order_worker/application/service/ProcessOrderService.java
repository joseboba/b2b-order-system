package com.b2b.order_worker.application.service;

import com.b2b.order_worker.application.ports.input.ProcessOrderUseCase;
import com.b2b.order_worker.application.ports.output.ClientPort;
import com.b2b.order_worker.application.ports.output.DeadLetterPort;
import com.b2b.order_worker.application.ports.output.OrderRepositoryPort;
import com.b2b.order_worker.application.ports.output.ProductPort;
import com.b2b.order_worker.domain.model.Client;
import com.b2b.order_worker.domain.model.Order;
import com.b2b.order_worker.domain.model.Product;
import com.b2b.order_worker.domain.service.TaxCalculationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessOrderService implements ProcessOrderUseCase {

    private final ClientPort clientPort;
    private final ProductPort productPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final DeadLetterPort deadLetterPort;
    private final TaxCalculationService taxCalculationService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> process(Order order) {
        return validateOrder(order)
                .then(Mono.defer(() -> orderRepositoryPort.existsById(order.orderId())))
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.info("Order {} already processed, skipping", order.orderId());
                        return Mono.<Void>empty();
                    }
                    return processNewOrder(order);
                })
                .onErrorResume(ex -> {
                    log.error("Failed to process order {}: {}", order.orderId(), ex.getMessage());
                    return deadLetterPort.send(
                            Optional.ofNullable(order.orderId()).orElse("UNKNOWN"),
                            ex.getMessage(),
                            1,
                            serialize(order)
                    );
                });
    }

    private Mono<Void> validateOrder(Order order) {
        if (order.orderId() == null || order.orderId().isBlank()) {
            return Mono.error(new IllegalArgumentException("orderId is required"));
        }
        if (order.clientId() == null || order.clientId().isBlank()) {
            return Mono.error(new IllegalArgumentException("clientId is required"));
        }
        if (order.items() == null || order.items().isEmpty()) {
            return Mono.error(new IllegalArgumentException("items cannot be empty"));
        }
        return Mono.empty();
    }

    private Mono<Void> processNewOrder(Order order) {
        Mono<Client> clientMono = clientPort.findById(order.clientId());

        Mono<List<Product>> productsMono = Flux.fromIterable(order.items())
                .flatMap(item -> productPort.findById(item.productId()))
                .collectList();

        return Mono.zip(clientMono, productsMono)
                .map(tuple -> taxCalculationService.enrich(order, tuple.getT1(), tuple.getT2()))
                .flatMap(orderRepositoryPort::save)
                .doOnSuccess(saved -> log.info("Order {} saved to MongoDB", saved.orderId()))
                .then();
    }

    private String serialize(Order order) {
        try {
            return objectMapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            return order.toString();
        }
    }
}
