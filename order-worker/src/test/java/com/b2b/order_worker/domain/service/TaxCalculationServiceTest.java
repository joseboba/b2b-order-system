package com.b2b.order_worker.domain.service;

import com.b2b.order_worker.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCalculationServiceTest {

    private TaxCalculationService service;

    private final Client client = new Client("CLI-001", "Test Corp", "MAYORISTA", "RESPONSABLE_IVA", "Cundinamarca");

    @BeforeEach
    void setUp() {
        service = new TaxCalculationService();
    }

    @Test
    void gravado_applies_19_percent_tax() {
        Order order = new Order("ORD-001", "CLI-001",
                List.of(new OrderItem("PRD-001", 10, new BigDecimal("2500.00"))));
        Product product = new Product("PRD-001", "Gaseosa 600ml", "GAS-600-PET", "GRAVADO");

        EnrichedOrder result = service.enrich(order, client, List.of(product));

        EnrichedItem item = result.items().get(0);
        assertThat(item.subtotal()).isEqualByComparingTo("25000.00");
        assertThat(item.taxRate()).isEqualByComparingTo("0.19");
        assertThat(item.taxAmount()).isEqualByComparingTo("4750.00");
        assertThat(item.lineTotal()).isEqualByComparingTo("29750.00");
    }

    @Test
    void reducido_applies_5_percent_tax() {
        Order order = new Order("ORD-002", "CLI-001",
                List.of(new OrderItem("PRD-008", 4, new BigDecimal("1800.00"))));
        Product product = new Product("PRD-008", "Harina de trigo 1kg", "HAR-1KG-TRI", "REDUCIDO");

        EnrichedOrder result = service.enrich(order, client, List.of(product));

        EnrichedItem item = result.items().get(0);
        assertThat(item.subtotal()).isEqualByComparingTo("7200.00");
        assertThat(item.taxRate()).isEqualByComparingTo("0.05");
        assertThat(item.taxAmount()).isEqualByComparingTo("360.00");
        assertThat(item.lineTotal()).isEqualByComparingTo("7560.00");
    }

    @Test
    void exento_applies_zero_tax() {
        Order order = new Order("ORD-003", "CLI-001",
                List.of(new OrderItem("PRD-002", 3, new BigDecimal("1200.00"))));
        Product product = new Product("PRD-002", "Agua mineral 500ml", "AGU-500-PET", "EXENTO");

        EnrichedOrder result = service.enrich(order, client, List.of(product));

        EnrichedItem item = result.items().get(0);
        assertThat(item.subtotal()).isEqualByComparingTo("3600.00");
        assertThat(item.taxRate()).isEqualByComparingTo("0.00");
        assertThat(item.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(item.lineTotal()).isEqualByComparingTo("3600.00");
    }

    @Test
    void summary_aggregates_multiple_items_correctly() {
        Order order = new Order("ORD-004", "CLI-001", List.of(
                new OrderItem("PRD-001", 2, new BigDecimal("2500.00")),
                new OrderItem("PRD-008", 1, new BigDecimal("1800.00")),
                new OrderItem("PRD-002", 5, new BigDecimal("1200.00"))
        ));
        List<Product> products = List.of(
                new Product("PRD-001", "Gaseosa 600ml", "GAS-600-PET", "GRAVADO"),
                new Product("PRD-008", "Harina de trigo 1kg", "HAR-1KG-TRI", "REDUCIDO"),
                new Product("PRD-002", "Agua mineral 500ml", "AGU-500-PET", "EXENTO")
        );

        EnrichedOrder result = service.enrich(order, client, products);

        // subtotals: 5000 + 1800 + 6000 = 12800
        assertThat(result.summary().subtotal()).isEqualByComparingTo("12800.00");
        // taxes: 950 + 90 + 0 = 1040
        assertThat(result.summary().totalTax()).isEqualByComparingTo("1040.00");
        assertThat(result.summary().grandTotal()).isEqualByComparingTo("13840.00");
        assertThat(result.summary().currency()).isEqualTo("COP");
    }

    @Test
    void client_data_is_mapped_to_enriched_order() {
        Order order = new Order("ORD-005", "CLI-001",
                List.of(new OrderItem("PRD-001", 1, new BigDecimal("1000.00"))));
        Product product = new Product("PRD-001", "Gaseosa", "GAS-001", "GRAVADO");

        EnrichedOrder result = service.enrich(order, client, List.of(product));

        assertThat(result.clientId()).isEqualTo("CLI-001");
        assertThat(result.clientName()).isEqualTo("Test Corp");
        assertThat(result.clientSegment()).isEqualTo("MAYORISTA");
        assertThat(result.clientTaxRegime()).isEqualTo("RESPONSABLE_IVA");
        assertThat(result.clientRegion()).isEqualTo("Cundinamarca");
    }

    @Test
    void bigdecimal_precision_is_maintained() {
        Order order = new Order("ORD-006", "CLI-001",
                List.of(new OrderItem("PRD-001", 3, new BigDecimal("333.33"))));
        Product product = new Product("PRD-001", "Item X", "ITM-001", "GRAVADO");

        EnrichedOrder result = service.enrich(order, client, List.of(product));

        EnrichedItem item = result.items().get(0);
        // subtotal = 333.33 * 3 = 999.99
        assertThat(item.subtotal()).isEqualByComparingTo("999.99");
        // taxAmount = 999.99 * 0.19 = 189.9981
        assertThat(item.taxAmount()).isEqualByComparingTo("189.9981");
        // No rounding applied — precision preserved
        assertThat(item.lineTotal()).isEqualByComparingTo("1189.9881");
    }
}
