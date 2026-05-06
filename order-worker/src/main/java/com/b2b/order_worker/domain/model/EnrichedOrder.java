package com.b2b.order_worker.domain.model;

import java.time.Instant;
import java.util.List;

public record EnrichedOrder(
        String orderId,
        String clientId,
        String clientName,
        String clientSegment,
        String clientTaxRegime,
        String clientRegion,
        List<EnrichedItem> items,
        OrderSummary summary,
        Instant processedAt
) {}
