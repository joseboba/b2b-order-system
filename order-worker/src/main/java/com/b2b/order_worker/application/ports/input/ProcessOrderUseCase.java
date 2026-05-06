package com.b2b.order_worker.application.ports.input;

import com.b2b.order_worker.domain.model.Order;
import reactor.core.publisher.Mono;

public interface ProcessOrderUseCase {
    Mono<Void> process(Order order);
}
