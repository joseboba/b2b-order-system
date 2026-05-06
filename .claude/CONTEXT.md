# Contexto de sesión — B2B Order System

> Proyecto: Prueba técnica senior — Worker Java Reactivo para Procesamiento de Pedidos B2B
> **Estado: COMPLETADO ✅**

---

## Stack y estructura

**Mono-repo en** `/home/jebo/Documents/CBC/b2b-order-system/`

```
b2b-order-system/
├── docker-compose.yml        ✅ completo y funcional
├── .env                      ✅ todas las variables externalizadas
├── send-test-order.sh        ✅ script bash de prueba end-to-end
├── README.md                 ✅ diagrama, variables, comandos, decisiones técnicas
├── producs-api/              ✅ Go/Gin, puerto 8081 (typo intencional en el dir)
├── clients-api/              ✅ NestJS, puerto 8082
└── order-worker/             ✅ Java 21 / Spring Boot 4.0.6 / WebFlux — COMPLETO
    ├── build.gradle
    ├── Dockerfile
    └── src/
        ├── main/java/com/b2b/order_worker/
        │   ├── domain/
        │   │   ├── enums/TaxCategory.java
        │   │   ├── model/  Order, OrderItem, Product, Client,
        │   │   │           EnrichedOrder, EnrichedItem, OrderSummary
        │   │   └── service/TaxCalculationService.java
        │   ├── application/
        │   │   ├── ports/input/ProcessOrderUseCase.java
        │   │   ├── ports/output/  ProductPort, ClientPort,
        │   │   │                  OrderRepositoryPort, DeadLetterPort
        │   │   └── service/ProcessOrderService.java
        │   └── infrastructure/
        │       ├── kafka/   KafkaOrderAdapter (@KafkaListener),
        │       │            KafkaDltAdapter (KafkaTemplate)
        │       ├── web/     ProductWebClientAdapter, ClientWebClientAdapter
        │       ├── persistence/ EnrichedOrderDocument, EnrichedOrderMongoRepository,
        │       │                MongoOrderRepositoryAdapter
        │       └── config/  KafkaConfig (ObjectMapper), RedisConfig,
        │                    WebClientConfig, DomainConfig
        └── test/java/com/b2b/order_worker/
            ├── domain/service/TaxCalculationServiceTest.java   (6 tests ✅)
            ├── application/service/ProcessOrderServiceTest.java (6 tests ✅)
            └── OrderWorkerApplicationTests.java  (2 tests Testcontainers ✅)
```

---

## Estado final — todas las fases completadas

| Fase | Estado |
|---|---|
| Fase 1 — Infraestructura (docker-compose.yml) | ✅ |
| Fase 2 — products-api (Go/Gin) | ✅ |
| Fase 3 — clients-api (NestJS) | ✅ |
| Fase 4 — order-worker (Java/WebFlux) | ✅ |
| Fase 5 — Testing (14/14 tests) | ✅ |
| Fase 6 — Entregables finales | ✅ |

---

## Criterios de evaluación — estado final

| Criterio | Peso | Estado |
|---|---|---|
| Flujo funcional completo Kafka → MongoDB | 30% | ✅ validado con Testcontainers |
| Calidad reactiva (sin `.block()`) | 20% | ✅ todo via `Mono`/`Flux`, cero `.block()` |
| Arquitectura hexagonal | 15% | ✅ dominio libre de frameworks |
| Testing (unitarios + Testcontainers) | 12% | ✅ 14 tests (12 unitarios + 2 integración) |
| Cálculo de impuesto con BigDecimal | 11% | ✅ `new BigDecimal("0.19")`, testado |
| Resiliencia (Retry + CB + DLT) | 8% | ✅ Resilience4j + KafkaDltAdapter |
| Docker Compose + README | 4% | ✅ completo |

**Descalificación automática:** `.block()` ✅ ninguno, URLs hardcodeadas ✅ ninguna.

---

## Decisiones técnicas clave

| Decisión | Razonamiento |
|---|---|
| `TaxCategory` como enum | Tasas vienen de la ley — type-safe, testeable sin mocks, sin I/O. |
| `new BigDecimal("0.19")` con String | Evita errores de punto flotante — criterio explícito de evaluación (11% del score). |
| `Mono.defer()` en el check de idempotencia | Sin `defer`, `.then(repo.existsById(null))` se evalúa eagerly → NPE cuando orderId es null. Aprendido en tests. |
| `doFinally` para ack | Garantiza ack tanto en éxito como en error — evita reprocessing infinito. |
| `@KafkaListener` (spring-kafka) en lugar de `KafkaReceiver` (reactor-kafka) | `reactor-kafka:1.3.x` usa la firma de constructor `ConsumerRecord(String,int,long,long,TimestampType,Long,int,int,K,V,Headers)` que fue eliminada en `kafka-clients:4.x`. Spring Boot 4.0.6 usa `kafka-clients:4.1.2`. Migrar a `@KafkaListener` de spring-kafka resuelve la incompatibilidad binaria. |
| `KafkaTemplate` para DLT (spring-kafka) | Consistente con la migración desde `KafkaSender` (reactor-kafka). `kafkaTemplate.send()` devuelve `CompletableFuture` directo en spring-kafka 4.x, sin `.completable()`. |
| Redis caché en el worker, no en las APIs | Las APIs son "tontas". El worker conoce el problema del lote. |
| `kafka-init` con `service_completed_successfully` | Garantiza que los tópicos existen antes del arranque del worker. Los perfiles `["app"]` fueron eliminados — todo levanta con un solo `docker compose up`. |
| `mongo:7.0` en docker-compose | Ajustado del spec (8.0.10) por compatibilidad. No cambiar sin verificar. |
| Enums TS: keys inglés CamelCase, valores español | El código habla inglés, el contrato de la API respeta el dominio en español. |
| `@MockitoBean` en tests Spring Boot 4 | Spring Boot 4 renombró `@MockBean` a `@MockitoBean`. |
| `ObjectMapper` como `@Bean` explícito en `KafkaConfig` | Spring Boot 4 WebFlux no auto-configura `ObjectMapper` en todos los escenarios — registrarlo explícitamente evita `NoSuchBeanDefinitionException`. |

---

## Comandos útiles

```bash
# Levantar TODO el sistema (infra + APIs + worker) con un solo comando
docker compose up -d --build

# Smoke tests
curl localhost:8081/products/PRD-001
curl localhost:8082/clients/CLI-99821
curl localhost:8080/actuator/health

# Build del worker (fuera de Docker, para desarrollo)
cd order-worker && ./gradlew bootJar -x test --no-daemon

# Tests unitarios del worker
./gradlew test --tests "*.TaxCalculationServiceTest" --tests "*.ProcessOrderServiceTest" --no-daemon

# Test de integración (requiere Docker)
./gradlew test --tests "com.b2b.order_worker.OrderWorkerApplicationTests" --no-daemon

# Suite completa (14 tests)
./gradlew test --no-daemon

# Enviar orden de prueba end-to-end
cd /home/jebo/Documents/CBC/b2b-order-system
./send-test-order.sh

# Ver documentos en MongoDB
docker exec b2b-mongodb mongosh b2b_orders --eval \
  'db["enriched-orders"].find().limit(5).pretty()' --quiet

# Verificar tópicos Kafka
docker exec b2b-kafka kafka-topics --list --bootstrap-server localhost:9092

# Estado de contenedores
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"
```
