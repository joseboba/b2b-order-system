package com.b2b.order_worker.domain.model;

public record Client(
        String clientId,
        String name,
        String segment,
        String taxRegime,
        String region
) {}
