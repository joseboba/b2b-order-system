package com.b2b.order_worker.infrastructure.persistence;

import com.b2b.order_worker.domain.model.EnrichedItem;
import com.b2b.order_worker.domain.model.EnrichedOrder;
import com.b2b.order_worker.domain.model.OrderSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "enriched-orders")
public class EnrichedOrderDocument {

    @Id
    private String id;       // _id interno de MongoDB — igual a orderId
    private String orderId;  // campo de negocio visible en el documento
    private String status;
    private ClientDoc client;
    private List<ItemDoc> items;
    private SummaryDoc summary;
    private Instant processedAt;

    public static EnrichedOrderDocument from(EnrichedOrder order) {
        return EnrichedOrderDocument.builder()
                .id(order.orderId())
                .orderId(order.orderId())
                .status("PROCESSED")
                .client(ClientDoc.from(order))
                .items(order.items().stream().map(ItemDoc::from).collect(Collectors.toList()))
                .summary(SummaryDoc.from(order.summary()))
                .processedAt(order.processedAt())
                .build();
    }

    public EnrichedOrder toDomain() {
        return new EnrichedOrder(
                orderId, client.getClientId(), client.getName(), client.getSegment(),
                client.getTaxRegime(), client.getRegion(),
                items.stream().map(ItemDoc::toDomain).collect(Collectors.toList()),
                summary.toDomain(),
                processedAt
        );
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientDoc {
        private String clientId;
        private String name;
        private String segment;
        private String taxRegime;
        private String region;

        static ClientDoc from(EnrichedOrder order) {
            return ClientDoc.builder()
                    .clientId(order.clientId())
                    .name(order.clientName())
                    .segment(order.clientSegment())
                    .taxRegime(order.clientTaxRegime())
                    .region(order.clientRegion())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDoc {
        private String productId;
        private String name;
        private String sku;
        private int quantity;
        private BigDecimal unitPrice;
        private String taxCategory;
        private BigDecimal taxRate;
        private BigDecimal subtotal;
        private BigDecimal taxAmount;
        private BigDecimal lineTotal;

        static ItemDoc from(EnrichedItem item) {
            return ItemDoc.builder()
                    .productId(item.productId())
                    .name(item.name())
                    .sku(item.sku())
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .taxCategory(item.taxCategory())
                    .taxRate(item.taxRate())
                    .subtotal(item.subtotal())
                    .taxAmount(item.taxAmount())
                    .lineTotal(item.lineTotal())
                    .build();
        }

        EnrichedItem toDomain() {
            return new EnrichedItem(productId, name, sku, quantity, unitPrice,
                    taxCategory, taxRate, subtotal, taxAmount, lineTotal);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryDoc {
        private BigDecimal subtotal;
        private BigDecimal totalTax;
        private BigDecimal grandTotal;
        private String currency;

        static SummaryDoc from(OrderSummary summary) {
            return SummaryDoc.builder()
                    .subtotal(summary.subtotal())
                    .totalTax(summary.totalTax())
                    .grandTotal(summary.grandTotal())
                    .currency(summary.currency())
                    .build();
        }

        OrderSummary toDomain() {
            return new OrderSummary(subtotal, totalTax, grandTotal, currency);
        }
    }
}
