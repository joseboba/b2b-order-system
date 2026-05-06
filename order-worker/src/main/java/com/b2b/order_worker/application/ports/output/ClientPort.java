package com.b2b.order_worker.application.ports.output;

import com.b2b.order_worker.domain.model.Client;
import reactor.core.publisher.Mono;

public interface ClientPort {
    Mono<Client> findById(String clientId);
}
