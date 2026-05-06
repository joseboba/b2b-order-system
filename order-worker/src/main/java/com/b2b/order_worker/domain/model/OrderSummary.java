package com.b2b.order_worker.domain.model;

import java.math.BigDecimal;

public record OrderSummary(
        BigDecimal subtotal,
        BigDecimal totalTax,
        BigDecimal grandTotal,
        String currency
) {}
