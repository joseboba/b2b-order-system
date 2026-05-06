package com.b2b.order_worker.domain.service;

import com.b2b.order_worker.domain.enums.TaxCategory;
import com.b2b.order_worker.domain.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TaxCalculationService {

    public EnrichedOrder enrich(Order order, Client client, List<Product> products) {
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::productId, p -> p));

        List<EnrichedItem> enrichedItems = order.items().stream()
                .map(item -> enrichItem(item, productMap.get(item.productId())))
                .collect(Collectors.toList());

        return new EnrichedOrder(
                order.orderId(),
                client.clientId(),
                client.name(),
                client.segment(),
                client.taxRegime(),
                client.region(),
                enrichedItems,
                buildSummary(enrichedItems),
                Instant.now()
        );
    }

    private EnrichedItem enrichItem(OrderItem item, Product product) {
        TaxCategory category = TaxCategory.valueOf(product.taxCategory());
        BigDecimal qty = BigDecimal.valueOf(item.quantity());
        BigDecimal subtotal = item.unitPrice().multiply(qty);
        BigDecimal taxAmount = subtotal.multiply(category.rate());
        BigDecimal lineTotal = subtotal.add(taxAmount);

        return new EnrichedItem(
                product.productId(),
                product.name(),
                product.sku(),
                item.quantity(),
                item.unitPrice(),
                product.taxCategory(),
                category.rate(),
                subtotal,
                taxAmount,
                lineTotal
        );
    }

    private OrderSummary buildSummary(List<EnrichedItem> items) {
        BigDecimal subtotal = items.stream()
                .map(EnrichedItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTax = items.stream()
                .map(EnrichedItem::taxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderSummary(subtotal, totalTax, subtotal.add(totalTax), "COP");
    }
}
