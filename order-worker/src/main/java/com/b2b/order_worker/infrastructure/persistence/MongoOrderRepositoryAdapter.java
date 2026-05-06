package com.b2b.order_worker.infrastructure.persistence;

import com.b2b.order_worker.application.ports.output.OrderRepositoryPort;
import com.b2b.order_worker.domain.model.EnrichedOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MongoOrderRepositoryAdapter implements OrderRepositoryPort {

    private final EnrichedOrderMongoRepository mongoRepository;

    @Override
    public Mono<Boolean> existsById(String orderId) {
        return mongoRepository.existsById(orderId);
    }

    @Override
    public Mono<EnrichedOrder> save(EnrichedOrder order) {
        return mongoRepository.save(EnrichedOrderDocument.from(order))
                .map(EnrichedOrderDocument::toDomain);
    }
}
