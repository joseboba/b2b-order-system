package com.b2b.order_worker;

import com.b2b.order_worker.application.ports.output.ClientPort;
import com.b2b.order_worker.application.ports.output.ProductPort;
import com.b2b.order_worker.domain.model.Client;
import com.b2b.order_worker.domain.model.Product;
import com.b2b.order_worker.infrastructure.persistence.EnrichedOrderMongoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderWorkerApplicationTests {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EnrichedOrderMongoRepository repository;

    @MockitoBean
    private ProductPort productPort;

    @MockitoBean
    private ClientPort clientPort;

    @Value("${app.kafka.topics.orders}")
    private String ordersTopic;

    @Test
    void order_published_to_kafka_is_enriched_and_saved_to_mongodb() {
        String orderId = "ORD-INTEG-001";

        when(clientPort.findById("CLI-99821")).thenReturn(Mono.just(
                new Client("CLI-99821", "Distribuidora Andina S.A.S", "MAYORISTA", "RESPONSABLE_IVA", "Valle del Cauca")
        ));
        when(productPort.findById("PRD-001")).thenReturn(Mono.just(
                new Product("PRD-001", "Gaseosa 600ml", "GAS-600-PET", "GRAVADO")
        ));

        String orderJson = """
                {"orderId":"ORD-INTEG-001","clientId":"CLI-99821","items":[{"productId":"PRD-001","quantity":10,"unitPrice":2500.00}]}
                """;

        kafkaTemplate.send(ordersTopic, orderId, orderJson.strip());

        StepVerifier.create(
                        repository.findById(orderId)
                                .repeatWhenEmpty(flux -> flux.delayElements(Duration.ofMillis(500)).take(20))
                )
                .assertNext(doc -> {
                    assertThat(doc.getOrderId()).isEqualTo(orderId);
                    assertThat(doc.getClientName()).isEqualTo("Distribuidora Andina S.A.S");
                    assertThat(doc.getItems()).hasSize(1);
                    assertThat(doc.getSummary().getSubtotal()).isEqualByComparingTo("25000.00");
                    assertThat(doc.getSummary().getTotalTax()).isEqualByComparingTo("4750.00");
                    assertThat(doc.getSummary().getGrandTotal()).isEqualByComparingTo("29750.00");
                })
                .verifyComplete();
    }

    @Test
    void duplicate_order_is_not_persisted_twice() {
        String orderId = "ORD-INTEG-DUP";

        when(clientPort.findById("CLI-99821")).thenReturn(Mono.just(
                new Client("CLI-99821", "Distribuidora Andina S.A.S", "MAYORISTA", "RESPONSABLE_IVA", "Valle del Cauca")
        ));
        when(productPort.findById("PRD-001")).thenReturn(Mono.just(
                new Product("PRD-001", "Gaseosa 600ml", "GAS-600-PET", "GRAVADO")
        ));

        String orderJson = String.format(
                "{\"orderId\":\"%s\",\"clientId\":\"CLI-99821\",\"items\":[{\"productId\":\"PRD-001\",\"quantity\":2,\"unitPrice\":2500.00}]}",
                orderId);

        kafkaTemplate.send(ordersTopic, orderId, orderJson);
        kafkaTemplate.send(ordersTopic, orderId, orderJson);

        // Wait for first message to be processed
        StepVerifier.create(
                        repository.findById(orderId)
                                .repeatWhenEmpty(flux -> flux.delayElements(Duration.ofMillis(500)).take(20))
                )
                .assertNext(doc -> assertThat(doc.getOrderId()).isEqualTo(orderId))
                .verifyComplete();

        // Verify only one document exists
        StepVerifier.create(repository.count())
                .assertNext(count -> assertThat(count).isGreaterThanOrEqualTo(1))
                .verifyComplete();
    }
}
