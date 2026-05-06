package com.b2b.order_worker.application.ports.output;

import com.b2b.order_worker.domain.model.Product;
import reactor.core.publisher.Mono;

public interface ProductPort {
    Mono<Product> findById(String productId);
}
