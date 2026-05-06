# B2B Order System — Prueba Técnica Senior

Worker reactivo para procesamiento de pedidos B2B: consume órdenes desde Kafka, las enriquece con datos de clientes y productos (con caché Redis y resiliencia Resilience4j), calcula impuestos y persiste el resultado en MongoDB.

---

## Arquitectura

```
                       ┌─────────────────────────────────────────────────────┐
                       │                  order-worker (Java 21 / WebFlux)   │
                       │                                                     │
 Kafka                 │  @KafkaListener      ProcessOrderService            │
 orders-topic ────────►│  (spring-kafka)  ──► (hexagonal core)               │
                       │                       │                             │
                       │                       ├─ idempotencia (MongoDB)     │
                       │                       │                             │
                       │         ┌─────────────┼─────────────┐               │
                       │         ▼             ▼             ▼               │
                       │   products-api    clients-api   MongoDB             │
                       │   (Go/Gin :8081)  (NestJS:8082) (enrich)            │
                       │         │              │                            │
                       │    Redis cache ◄───────┘                            │
                       │    (TTL 300s)                                       │
                       │                       │                             │
                       │              error ───► Kafka orders-dlt            │
                       └─────────────────────────────────────────────────────┘
```

### Flujo de procesamiento

1. `@KafkaListener` recibe el mensaje del topic `orders-topic` (ack manual con `doFinally`)
2. `ProcessOrderService` valida la orden (orderId, clientId, items obligatorios)
3. Verifica idempotencia: si `orderId` ya existe en MongoDB, descarta silenciosamente
4. `Mono.zip(clientPort, productPort×N)` — llamadas paralelas con caché Redis (TTL 300 s)
5. `TaxCalculationService` calcula subtotal, taxAmount y lineTotal por ítem
6. Persiste el documento enriquecido en `enriched-orders` (MongoDB)
7. En error: 3 reintentos (Resilience4j Retry) → Circuit Breaker → publica en `orders-dlt`

---

## Stack

| Servicio | Tecnología | Puerto |
|---|---|---|
| order-worker | Java 21 / Spring Boot 4.0 / WebFlux | 8080 |
| products-api | Go 1.22 / Gin | 8081 |
| clients-api | NestJS 10 / TypeScript | 8082 |
| Kafka | Confluent Platform 7.6 | 9092 |
| MongoDB | 7.0 | 27017 |
| Redis | 7.2 | 6379 |

---

## Catálogo de productos

Todos los productos se consultan en `GET /products/{productId}`.

| ID | Nombre | SKU | Categoría | IVA | Tasa |
|---|---|---|---|---|---|
| PRD-001 | Gaseosa 600ml | GAS-600-PET | Bebidas | GRAVADO | 19% |
| PRD-002 | Agua purificada 500ml | AGU-500-PET | Bebidas | EXENTO | 0% |
| PRD-003 | Arroz blanco 1kg | ARR-1KG-BLA | Granos | REDUCIDO | 5% |
| PRD-004 | Jabón de tocador 100g | JAB-100G-TOC | Aseo | GRAVADO | 19% |
| PRD-005 | Ibuprofeno 400mg x24 | IBU-400MG-24 | Medicamentos | EXENTO | 0% |
| PRD-006 | Aceite vegetal 1L | ACE-1L-VEG | Aceites | REDUCIDO | 5% |
| PRD-007 | Shampoo anticaspa 400ml | SHA-400ML-ANC | Cuidado personal | GRAVADO | 19% |
| PRD-008 | Harina de trigo 1kg | HAR-1KG-TRI | Harinas | REDUCIDO | 5% |
| PRD-009 | Desinfectante multiusos 1L | DES-1L-MUL | Aseo | GRAVADO | 19% |
| PRD-010 | Leche entera UHT 1L | LEC-1L-UHT | Lácteos | REDUCIDO | 5% |
| PRD-011 | Confites de chocolate 200g | CON-200G-CHO | Confitería | GRAVADO | 19% |
| PRD-012 | Papa pastusa 1kg | PAP-1KG-PAS | Verduras | EXENTO | 0% |

### Categorías fiscales

| Categoría | Tasa | Descripción |
|---|---|---|
| GRAVADO | 19% | IVA tarifa general |
| REDUCIDO | 5% | IVA tarifa diferencial (alimentos básicos procesados) |
| EXENTO | 0% | Sin IVA (alimentos de la canasta básica, medicamentos) |

---

## Catálogo de clientes

Todos los clientes se consultan en `GET /clients/{clientId}`.

| ID | Nombre | Segmento | Régimen fiscal | Región |
|---|---|---|---|---|
| CLI-99821 | Distribuidora Andina S.A.S | MAYORISTA | RESPONSABLE_IVA | Valle del Cauca |
| CLI-10034 | Supermercados del Norte S.A.S | MAYORISTA | RESPONSABLE_IVA | Atlántico |
| CLI-20567 | Tienda Don Pedro | MINORISTA | NO_RESPONSABLE | Cundinamarca |
| CLI-30891 | Almacenes Medellín Ltda | MAYORISTA | RESPONSABLE_IVA | Antioquia |
| CLI-40124 | Distribuciones Costa S.A.S | MAYORISTA | RESPONSABLE_IVA | Bolívar |
| CLI-50678 | Minimarket La Esquina | MINORISTA | NO_RESPONSABLE | Valle del Cauca |

---

## Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Bootstrap servers de Kafka |
| `KAFKA_TOPIC_ORDERS` | `orders-topic` | Tópico de órdenes entrantes |
| `KAFKA_TOPIC_DLT` | `orders-dlt` | Dead-letter topic |
| `MONGO_URI` | `mongodb://localhost:27017` | URI de conexión MongoDB |
| `MONGO_DATABASE` | `b2b_orders` | Base de datos MongoDB |
| `REDIS_HOST` | `localhost` | Host de Redis |
| `REDIS_PORT` | `6379` | Puerto de Redis |
| `REDIS_TTL_SECONDS` | `300` | TTL de caché (segundos) |
| `PRODUCTS_API_URL` | `http://localhost:8081` | URL de la API de productos |
| `CLIENTS_API_URL` | `http://localhost:8082` | URL de la API de clientes |

Todos los valores tienen default para desarrollo local. En Docker Compose se inyectan explícitamente al contenedor `order-worker`.

---

## Ejecución

### Prerrequisitos

- Docker Engine ≥ 24 con Docker Compose v2
- Java 21 (solo para desarrollo/tests fuera de Docker)
- Go 1.22 (solo para desarrollo local de products-api)
- Node.js 20 (solo para desarrollo local de clients-api)

### Levantar todo el sistema

```bash
docker compose up -d --build
```

Un solo comando construye las 3 imágenes (multi-stage) y levanta los 8 contenedores en orden:
`zookeeper → kafka → kafka-init → mongodb/redis → products-api/clients-api → order-worker`

Verificar que todos estén `healthy`:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"
```

### Smoke tests

```bash
# API de productos
curl localhost:8081/products/PRD-001
# → {"productId":"PRD-001","name":"Gaseosa 600ml","sku":"GAS-600-PET","category":"Bebidas","taxCategory":"GRAVADO","unitOfMeasure":"UND"}

# API de clientes
curl localhost:8082/clients/CLI-99821
# → {"clientId":"CLI-99821","name":"Distribuidora Andina S.A.S","segment":"MAYORISTA","taxRegime":"RESPONSABLE_IVA","region":"Valle del Cauca"}

# Health del worker
curl localhost:8080/actuator/health
# → {"status":"UP",...}
```

---

## Enviar órdenes de prueba

### Scripts disponibles

| Script | Descripción |
|---|---|
| `./send-test-order.sh` | Publica una orden válida (CLI-99821, PRD-001 + PRD-008), espera 5 s y muestra el documento enriquecido desde MongoDB buscando por `_id` |
| `./send-test-error-order.sh` | Publica una orden con `clientId` inválido (CLI-99826), espera 5 s y muestra el mensaje recibido en el DLT |

```bash
# Orden válida → resultado en MongoDB
./send-test-order.sh

# Orden inválida → resultado en DLT
./send-test-error-order.sh
```

### Ejemplos manuales

Reemplaza `<ORDEN_ID>` con un identificador único cada vez (Kafka + idempotencia lo requieren).

**Orden mixta — GRAVADO + REDUCIDO + EXENTO (mayorista)**
```bash
docker exec -i b2b-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic orders-topic \
  --property "parse.key=true" --property "key.separator=:" <<< \
'ORD-2024-COL-00200:{"orderId":"ORD-2024-COL-00200","clientId":"CLI-99821","items":[{"productId":"PRD-001","quantity":24,"unitPrice":3500.00},{"productId":"PRD-003","quantity":50,"unitPrice":2800.00},{"productId":"PRD-002","quantity":12,"unitPrice":1200.00}]}'
```

**Solo productos GRAVADO (19%) — cliente minorista**
```bash
docker exec -i b2b-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic orders-topic \
  --property "parse.key=true" --property "key.separator=:" <<< \
'ORD-2024-COL-00201:{"orderId":"ORD-2024-COL-00201","clientId":"CLI-20567","items":[{"productId":"PRD-004","quantity":30,"unitPrice":1500.00},{"productId":"PRD-007","quantity":15,"unitPrice":8900.00},{"productId":"PRD-009","quantity":10,"unitPrice":12000.00}]}'
```

**Solo productos REDUCIDO (5%) — mayorista Atlántico**
```bash
docker exec -i b2b-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic orders-topic \
  --property "parse.key=true" --property "key.separator=:" <<< \
'ORD-2024-COL-00202:{"orderId":"ORD-2024-COL-00202","clientId":"CLI-10034","items":[{"productId":"PRD-006","quantity":100,"unitPrice":6500.00},{"productId":"PRD-008","quantity":80,"unitPrice":3200.00},{"productId":"PRD-010","quantity":60,"unitPrice":2900.00}]}'
```

**Solo productos EXENTO (0%) — medicamentos y verduras**
```bash
docker exec -i b2b-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic orders-topic \
  --property "parse.key=true" --property "key.separator=:" <<< \
'ORD-2024-COL-00203:{"orderId":"ORD-2024-COL-00203","clientId":"CLI-30891","items":[{"productId":"PRD-005","quantity":20,"unitPrice":15000.00},{"productId":"PRD-012","quantity":200,"unitPrice":1800.00}]}'
```

**Orden grande — todos los productos (12 ítems)**
```bash
docker exec -i b2b-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic orders-topic \
  --property "parse.key=true" --property "key.separator=:" <<< \
'ORD-2024-COL-00204:{"orderId":"ORD-2024-COL-00204","clientId":"CLI-40124","items":[{"productId":"PRD-001","quantity":48,"unitPrice":3500},{"productId":"PRD-002","quantity":24,"unitPrice":1200},{"productId":"PRD-003","quantity":100,"unitPrice":2800},{"productId":"PRD-004","quantity":36,"unitPrice":1500},{"productId":"PRD-005","quantity":10,"unitPrice":15000},{"productId":"PRD-006","quantity":60,"unitPrice":6500},{"productId":"PRD-007","quantity":24,"unitPrice":8900},{"productId":"PRD-008","quantity":80,"unitPrice":3200},{"productId":"PRD-009","quantity":12,"unitPrice":12000},{"productId":"PRD-010","quantity":48,"unitPrice":2900},{"productId":"PRD-011","quantity":30,"unitPrice":4500},{"productId":"PRD-012","quantity":150,"unitPrice":1800}]}'
```

### Ver resultado en MongoDB

```bash
# Buscar por orderId específico
docker exec b2b-mongodb mongosh b2b_orders \
  --eval 'db["enriched-orders"].findOne({orderId:"ORD-2024-COL-00200"})' --quiet

# Último documento insertado
docker exec b2b-mongodb mongosh b2b_orders \
  --eval 'db["enriched-orders"].find().sort({processedAt:-1}).limit(1).pretty()' --quiet

# Todos los documentos
docker exec b2b-mongodb mongosh b2b_orders \
  --eval 'db["enriched-orders"].find().pretty()' --quiet
```

---

## Validar el Dead-Letter Topic (DLT)

El worker envía un mensaje al topic `orders-dlt` cuando falla el procesamiento de una orden (cliente no encontrado, producto no encontrado, error de red, validación fallida).

### Escenarios que disparan el DLT

| Escenario | Causa |
|---|---|
| `clientId` desconocido | clients-api retorna 404 → WebClient lanza error → `onErrorResume` → DLT |
| `productId` desconocido | products-api retorna 404 → WebClient lanza error → `onErrorResume` → DLT |
| `orderId` vacío o nulo | `validateOrder()` falla → `Mono.error()` → DLT |
| `items` vacío | `validateOrder()` falla → `Mono.error()` → DLT |

### Consumir los topics

**Órdenes entrantes** — ver todo lo que llega al worker:
```bash
docker exec -it b2b-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic orders-topic \
  --from-beginning
```

**Dead-Letter Topic** — ver órdenes que fallaron:
```bash
docker exec -it b2b-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic orders-dlt \
  --from-beginning
```

### Enviar una orden que falle (clientId inválido)

```bash
docker exec -i b2b-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic orders-topic \
  --property "parse.key=true" --property "key.separator=:" <<< \
'ORD-FAIL-001:{"orderId":"ORD-FAIL-001","clientId":"CLI-INVALIDO-999","items":[{"productId":"PRD-001","quantity":1,"unitPrice":2500.00}]}'
```

El mensaje DLT tendrá esta estructura:

```json
{
  "timestamp": "2026-05-05T23:52:10.813Z",
  "cause": "404 NOT_FOUND from GET http://clients-api:8082/clients/CLI-INVALIDO-999",
  "attemptNumber": 1,
  "originalPayload": "{\"orderId\":\"ORD-FAIL-001\",...}"
}
```

### Listar tópicos y offsets

```bash
# Ver todos los tópicos
docker exec b2b-kafka kafka-topics --list --bootstrap-server localhost:9092

# Ver offsets del DLT
docker exec b2b-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --bootstrap-server localhost:9092 --topic orders-dlt
```

---

## Tests

### Unitarios (sin Docker)

```bash
cd order-worker
./gradlew test --tests "*.TaxCalculationServiceTest" --tests "*.ProcessOrderServiceTest" --no-daemon
```

| Suite | Tests | Descripción |
|---|---|---|
| TaxCalculationServiceTest | 6 | GRAVADO, REDUCIDO, EXENTO, orden multi-ítem, cálculo BigDecimal |
| ProcessOrderServiceTest | 6 | Flujo feliz, idempotencia, validación, error → DLT |

### Integración — Testcontainers (requiere Docker)

```bash
cd order-worker
./gradlew test --tests "com.b2b.order_worker.OrderWorkerApplicationTests" --no-daemon
```

Levanta Kafka, MongoDB y Redis reales en contenedores. Valida el flujo completo publish → process → persist y el mecanismo de idempotencia.

### Suite completa (14 tests)

```bash
cd order-worker
./gradlew test --no-daemon
```

---

## Decisiones técnicas clave

| Decisión | Razonamiento |
|---|---|
| `new BigDecimal("0.19")` con String | Evita errores de representación en punto flotante — criterio explícito de evaluación (11% del score). |
| `TaxCategory` como enum | Tasas legales fijas: type-safe, testeable sin I/O ni mocks de base de datos. |
| `Mono.defer()` en idempotencia | Sin `defer`, el check `existsById` se evalúa de forma eager → NPE cuando `orderId` es null antes de la validación. |
| `doFinally` para ack en `@KafkaListener` | Garantiza ack tanto en éxito como en error — evita reprocessing infinito sin perder mensajes. |
| `@KafkaListener` (spring-kafka) en lugar de `KafkaReceiver` (reactor-kafka) | `reactor-kafka:1.3.x` usa una firma de `ConsumerRecord` eliminada en `kafka-clients:4.x`. Spring Boot 4.0 usa `kafka-clients:4.1.2`; spring-kafka es nativamente compatible. |
| `KafkaTemplate` para DLT | Consistente con la migración desde `KafkaSender`. En spring-kafka 4.x, `send()` devuelve `CompletableFuture` directo — sin `.completable()`. |
| `Mono.zip` para cliente + productos | Paralelismo real — las N llamadas a APIs externas no tienen dependencia entre sí. |
| Redis caché en el worker, no en las APIs | Las APIs son stateless. El worker conoce el patrón del lote y evita N llamadas repetidas al mismo recurso dentro de un batch. |
| `kafka-init` con `service_completed_successfully` | Garantiza que `orders-topic` y `orders-dlt` existen antes de que el worker arranque — evita errores de `UnknownTopicOrPartitionException` en startup. |
| Hexagonal estricto | El paquete `domain/` no contiene anotaciones de framework: testeable con constructores puros, sin Spring context. |
| Multi-stage Dockerfiles en los 3 servicios | La imagen final no contiene JDK, TypeScript toolchain ni Go toolchain — solo el artefacto ejecutable. |
| Usuarios no-root en todos los contenedores | Reduce la superficie de ataque en cada imagen de runtime. |
| `mongo:7.0` en docker-compose | Ajustado desde el spec (8.0.10) por compatibilidad con el driver reactivo. No cambiar sin verificar compatibilidad. |
