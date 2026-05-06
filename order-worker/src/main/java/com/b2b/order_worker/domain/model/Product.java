package com.b2b.order_worker.domain.model;

public record Product(
        String productId,
        String name,
        String sku,
        String taxCategory
) {}
