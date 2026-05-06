package com.b2b.order_worker.infrastructure.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface EnrichedOrderMongoRepository extends ReactiveMongoRepository<EnrichedOrderDocument, String> {
}
