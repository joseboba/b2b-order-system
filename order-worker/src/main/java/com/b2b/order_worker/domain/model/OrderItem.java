package com.b2b.order_worker.domain.model;

public record OrderItem(
        String productId,
        int quantity,
        java.math.BigDecimal unitPrice
) {}
