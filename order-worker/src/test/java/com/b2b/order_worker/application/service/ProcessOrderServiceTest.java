package com.b2b.order_worker.application.service;

import com.b2b.order_worker.application.ports.output.ClientPort;
import com.b2b.order_worker.application.ports.output.DeadLetterPort;
import com.b2b.order_worker.application.ports.output.OrderRepositoryPort;
import com.b2b.order_worker.application.ports.output.ProductPort;
import com.b2b.order_worker.domain.model.*;
import com.b2b.order_worker.domain.service.TaxCalculationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessOrderServiceTest {

    @Mock private ClientPort clientPort;
    @Mock private ProductPort productPort;
    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private DeadLetterPort deadLetterPort;

    private ProcessOrderService service;

    private final Client testClient = new Client("CLI-001", "Test Corp", "MAYORISTA", "RESPONSABLE_IVA", "Cundinamarca");
    private final Product testProduct = new Product("PRD-001", "Gaseosa", "GAS-600-PET", "GRAVADO");
    private final Order validOrder = new Order("ORD-001", "CLI-001", List.of(new OrderItem("PRD-001", 5, new BigDecimal("2500.00"))));

    @BeforeEach
    void setUp() {
        service = new ProcessOrderService(
                clientPort, productPort, orderRepositoryPort, deadLetterPort,
                new TaxCalculationService(), new ObjectMapper()
        );
    }

    @Test
    void process_valid_order_saves_enriched_order() {
        EnrichedOrder enriched = buildEnrichedOrder();
        when(orderRepositoryPort.existsById("ORD-001")).thenReturn(Mono.just(false));
        when(clientPort.findById("CLI-001")).thenReturn(Mono.just(testClient));
        when(productPort.findById("PRD-001")).thenReturn(Mono.just(testProduct));
        when(orderRepositoryPort.save(any())).thenReturn(Mono.just(enriched));

        StepVerifier.create(service.process(validOrder))
                .verifyComplete();

        verify(orderRepositoryPort).save(any(EnrichedOrder.class));
        verify(deadLetterPort, never()).send(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void process_duplicate_order_is_skipped() {
        when(orderRepositoryPort.existsById("ORD-001")).thenReturn(Mono.just(true));

        StepVerifier.create(service.process(validOrder))
                .verifyComplete();

        verify(clientPort, never()).findById(anyString());
        verify(orderRepositoryPort, never()).save(any());
    }

    @Test
    void process_order_with_missing_orderId_sends_to_dlt() {
        Order invalid = new Order(null, "CLI-001", List.of(new OrderItem("PRD-001", 1, new BigDecimal("2500.00"))));
        when(deadLetterPort.send(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.process(invalid))
                .verifyComplete();

        verify(deadLetterPort).send(eq("UNKNOWN"), anyString(), eq(1), anyString());
    }

    @Test
    void process_order_with_empty_items_sends_to_dlt() {
        Order invalid = new Order("ORD-002", "CLI-001", List.of());
        when(deadLetterPort.send(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.process(invalid))
                .verifyComplete();

        verify(deadLetterPort).send(eq("ORD-002"), anyString(), eq(1), anyString());
    }

    @Test
    void process_order_when_client_not_found_sends_to_dlt() {
        when(orderRepositoryPort.existsById("ORD-001")).thenReturn(Mono.just(false));
        when(clientPort.findById("CLI-001")).thenReturn(Mono.error(new RuntimeException("client not found")));
        when(deadLetterPort.send(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.process(validOrder))
                .verifyComplete();

        verify(deadLetterPort).send(eq("ORD-001"), contains("client not found"), eq(1), anyString());
    }

    @Test
    void process_order_when_product_not_found_sends_to_dlt() {
        when(orderRepositoryPort.existsById("ORD-001")).thenReturn(Mono.just(false));
        when(clientPort.findById("CLI-001")).thenReturn(Mono.just(testClient));
        when(productPort.findById("PRD-001")).thenReturn(Mono.error(new RuntimeException("product not found")));
        when(deadLetterPort.send(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.process(validOrder))
                .verifyComplete();

        verify(deadLetterPort).send(eq("ORD-001"), contains("product not found"), eq(1), anyString());
    }

    private EnrichedOrder buildEnrichedOrder() {
        EnrichedItem item = new EnrichedItem(
                "PRD-001", "Gaseosa", "GAS-600-PET", 5,
                new BigDecimal("2500.00"), "GRAVADO", new BigDecimal("0.19"),
                new BigDecimal("12500.00"), new BigDecimal("2375.00"), new BigDecimal("14875.00")
        );
        OrderSummary summary = new OrderSummary(
                new BigDecimal("12500.00"), new BigDecimal("2375.00"), new BigDecimal("14875.00"), "COP"
        );
        return new EnrichedOrder("ORD-001", "CLI-001", "Test Corp",
                "MAYORISTA", "RESPONSABLE_IVA", "Cundinamarca", List.of(item), summary, Instant.now());
    }
}
