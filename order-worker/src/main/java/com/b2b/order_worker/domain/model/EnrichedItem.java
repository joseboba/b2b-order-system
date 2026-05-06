package com.b2b.order_worker.domain.model;

import java.math.BigDecimal;

public record EnrichedItem(
        String productId,
        String name,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        String taxCategory,
        BigDecimal taxRate,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal lineTotal
) {}
