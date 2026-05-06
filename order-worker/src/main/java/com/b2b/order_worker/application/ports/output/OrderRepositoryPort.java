package com.b2b.order_worker.application.ports.output;

import com.b2b.order_worker.domain.model.EnrichedOrder;
import reactor.core.publisher.Mono;

public interface OrderRepositoryPort {
    Mono<Boolean> existsById(String orderId);
    Mono<EnrichedOrder> save(EnrichedOrder order);
}
