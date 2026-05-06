package com.b2b.order_worker.domain.model;

import java.util.List;

public record Order(
        String orderId,
        String clientId,
        List<OrderItem> items
) {}
