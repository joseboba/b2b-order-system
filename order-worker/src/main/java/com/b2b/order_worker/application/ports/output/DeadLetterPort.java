package com.b2b.order_worker.application.ports.output;

import reactor.core.publisher.Mono;

public interface DeadLetterPort {
    Mono<Void> send(String orderId, String cause, int attemptNumber, String originalPayload);
}
