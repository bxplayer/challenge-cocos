# Cocos Broker API

API REST de un broker/trading que expone, sobre los datos provistos:

- **Portfolio**: valor total de la cuenta, pesos disponibles y posiciones (cantidad, valor de mercado y rendimiento).
- **Búsqueda de instrumentos**: por ticker y/o nombre, paginada.
- **Órdenes**: envío (MARKET/LIMIT, BUY/SELL, por cantidad o por monto) y cancelación.

Todo el estado (tenencia y cash) se **deriva de la tabla `orders`**; no hay tabla de posiciones.

---

## Stack

- **Java 21** (LTS) + **Spring Boot 3.3**
- **PostgreSQL** (Docker) — persistencia
- **Redis** (Docker) — idempotencia de órdenes + cache-aside del portfolio
- **Spring Data JPA**, **springdoc-openapi** (Swagger), **Testcontainers**
- Build: **Maven** (con wrapper `./mvnw`)

## Arquitectura (hexagonal, pragmática)

```
domain/          núcleo puro (sin Spring/JPA): modelos, enums, PortfolioCalculator,
                 y puertos de salida (interfaces)
application/     casos de uso (@Service @Transactional)
infrastructure/  adaptadores: web (controllers + DTOs + ProblemDetail),
                 persistence (JPA), cache (Redis)
```

Regla de dependencias: `infrastructure → application → domain`. El dominio no conoce a Spring; los
puertos se implementan en `infrastructure`.

---

## Requisitos

- **Docker** + **Docker Compose**
- **JDK 21** (Eclipse Temurin recomendado). `./mvnw` usa el JDK que indique `JAVA_HOME`.

---

## Cómo levantar (local)

1) **Variables de entorno** (opcional; hay defaults):

```bash
cp .env.example .env
```

2) **Infra** (Postgres + Redis). El schema y los datos semilla se cargan solos en la primera inicialización:

```bash
docker compose up -d
```

3) **Aplicación**:

```bash
./mvnw spring-boot:run
```

La API queda en `http://localhost:8080`.

- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **Health**: http://localhost:8080/actuator/health

Para reiniciar la base al estado semilla: `docker compose down -v && docker compose up -d`.

### Alternativa: todo dockerizado

La app también se puede correr en contenedor (Temurin 21), sin instalar Java localmente. Está detrás del
profile `full`, así que el `docker compose up -d` de arriba **no** la levanta (queda para desarrollo con `mvnw`):

```bash
docker compose --profile full up -d --build
```

Esto construye la imagen (build con JDK 21) y levanta **Postgres + Redis + app**. La API queda en
`http://localhost:8080`. Para bajar todo: `docker compose --profile full down`.

---

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET`  | `/api/v1/users/{userId}/portfolio` | Valor total, cash disponible y posiciones |
| `GET`  | `/api/v1/instruments?query=&page=&size=&sort=` | Búsqueda paginada por ticker/nombre |
| `POST` | `/api/v1/orders` | Enviar orden (acepta header `Idempotency-Key`) |
| `POST` | `/api/v1/orders/{orderId}/cancel` | Cancelar una orden `NEW` del usuario (body: `{ "userId": 1 }`) |

Colección de ejemplos para importar en Postman: [`postman/`](./postman) (colección + environment local).

### Body de `POST /api/v1/orders`

```jsonc
{
  "userId": 1,
  "instrumentId": 54,
  "side": "BUY",          // BUY | SELL
  "type": "MARKET",       // MARKET | LIMIT
  "size": 10,             // cantidad de acciones …
  "amount": 100000,       // … o monto en pesos (excluyentes)
  "price": 200            // obligatorio solo para LIMIT
}
```

Respuestas: `201` con la orden creada (incluso si `REJECTED`); errores con `application/problem+json`:

- `400` forma del request (campos requeridos, signos, JSON malformado, parámetros inválidos).
- `404` usuario/instrumento/orden inexistente (una orden de otro usuario se trata como inexistente).
- `409` cancelar una orden que no está `NEW`; reintento con un `Idempotency-Key` cuya solicitud original sigue en curso.
- `422` reglas de la orden: exactamente uno de `size`/`amount`, `price` obligatorio en LIMIT, side `BUY`/`SELL`,
  instrumento de tipo `ACCIONES` (el `ARS` no se opera), monto que no alcanza para una acción, sin precio de mercado.

---

## Decisiones de diseño y suposiciones

- **Node.js → Java/Spring Boot.** El spec sugería Node; se implementó en Java por requerimiento del ejercicio.
- **Schema.** Se parte del dump provisto y se genera `db/init/01-schema.sql` limpio para Postgres local
  (se quitan `CREATE DATABASE`, owners y grants de Neon; ids con `IDENTITY`). Nombres de columnas en minúscula.
  El dump original solo tenía claves primarias y foráneas; se agregaron dos cosas, sin cambiar nombres ni tablas:
  - **Índices** en `orders(userid)` y `marketdata(instrumentid, date DESC)`: son las dos consultas que corren en
    cada request (todas las órdenes del usuario para portfolio/validación, y el último precio por instrumento).
    Sin ellos cada request recorre las tablas completas.
  - **`NOT NULL` y `CHECK`** en `orders` (`size > 0`, `price > 0`, `type`/`side`/`status` dentro de los valores
    válidos) y en `marketdata` (`instrumentid`, `close`, `date`): la base garantiza los invariantes que el
    cálculo del portfolio asume, aunque escriba otro proceso. `high/low/open` quedan nullable porque el dataset
    trae filas sin esos valores.
- **Reservas.** Las órdenes **LIMIT en `NEW` reservan** cash (BUY) o acciones (SELL); el disponible descuenta
  esas reservas. Cancelar una `NEW` libera la reserva.
- **Rechazo como estado de negocio.** Si no hay fondos/acciones, la orden se **persiste `REJECTED`** y se
  devuelve **`201`** con `status=REJECTED` (no es un error HTTP). Los errores de *request* sí son `4xx`.
- **Precios.** MARKET usa el último `close`; LIMIT usa el `price` enviado. Todo en pesos y con `BigDecimal`.
- **Orden por monto.** `size = floor(amount / price)` (no se admiten fracciones de acción).
- **Portfolio.**
  - Posición por instrumento = Σ `size`(BUY FILLED) − Σ `size`(SELL FILLED); se listan solo las de cantidad > 0.
  - Valor de mercado = último `close` × cantidad.
  - **Rendimiento total %** = (valorMercado − costo) / costo × 100, con costo = precio promedio ponderado de compras FILLED.
  - **Retorno diario %** = (`close` − `previousClose`) / `previousClose` × 100 (dato complementario).
  - **Valor total de cuenta** = cash total + valor de mercado de posiciones (el cash reservado sigue siendo patrimonio).
- **Sin autenticación** (según el spec): `userId` viaja por path (portfolio) o en el body (órdenes).
- **Concurrencia.** `SendOrderService` toma un **lock de fila sobre el usuario** (`SELECT … FOR UPDATE`) dentro de la
  transacción: dos órdenes simultáneas del mismo usuario se procesan en serie y la segunda ve la primera al validar
  fondos/acciones. Sin esto, un *check-then-act* concurrente permitiría sobregirar la cuenta.
- **Fechas.** `datetime` se genera con un `Clock` inyectado en UTC (fijable en tests).
- **Redis.** Idempotencia por header `Idempotency-Key` en `POST /orders`: la clave se **reserva atómicamente**
  (`SET NX`) antes de ejecutar el caso de uso y se scopea por `userId`; un reintento devuelve la misma orden, y si el
  primer intento sigue en curso responde `409`. También **cache-aside** del portfolio, **invalidado por escritura** en
  los servicios (cubre NEW/FILLED/CANCELLED).
  Los adaptadores son **tolerantes a fallos**: si Redis no está, degradan a miss/no-op sin afectar la operación.
  La idempotencia se puede **prender/apagar** (`IDEMPOTENCY_ENABLED`, default `true`) y su TTL es configurable
  (`IDEMPOTENCY_KEY_TTL_SECONDS`, default 86400 = 24h); el TTL del cache del portfolio también es configurable
  (`PORTFOLIO_CACHE_TTL_SECONDS`, default 60).
- **Sin colas (SQS/otros).** Se evaluó y se descartó: en este alcance **no hay procesamiento asíncrono real**
  (el spec indica no simular el mercado; las LIMIT quedan `NEW`) y la **ejecución de órdenes debe ser síncrona y
  transaccional**, devolviendo el estado final. Una cola solo aportaría si existiera un **matching engine** que
  ejecute las LIMIT contra el mercado — ese es el punto de extensión natural (ver `SendOrderService`).
- **Observabilidad.** Logging estándar (SLF4J) en los casos de uso.

---

## Testing

```bash
./mvnw test      # unitarios + capa web (Surefire): PortfolioCalculatorTest, OrderControllerTest
./mvnw verify    # + integración (Failsafe): SendOrderServiceIT con Testcontainers
```

- **`PortfolioCalculatorTest`** (unit, sin Spring): cash disponible con reservas, acciones disponibles, valor total,
  posiciones y rendimientos.
- **`OrderControllerTest`** (`@WebMvcTest`, sin DB ni Redis): 201, validación del request (400 con `errors[]`) y
  mapeo de errores a `ProblemDetail`, incluidos los propios de Spring (ruta inexistente → 404, no 500).
- **`SendOrderServiceIT`** (integración, Postgres + Redis reales vía Testcontainers): MARKET/LIMIT, BUY/SELL,
  por-monto (floor), rechazo por fondos y por acciones, reserva de acciones por LIMIT SELL, rechazo del instrumento
  `ARS`, y cancelación de `NEW` (propia y ajena).

> Los tests de integración requieren Docker en ejecución.

---

## Estructura del proyecto

```
src/main/java/com/cocos/broker/
├── domain/            modelos, enums, PortfolioCalculator, port/
├── application/       SendOrderService, CancelOrderService, PortfolioService, InstrumentService
└── infrastructure/
    ├── web/           controllers, dto/, error/ (ProblemDetail), OpenApiConfig
    ├── persistence/   entity/, repository/ (Spring Data), adapter/ (implementa los puertos)
    └── cache/         RedisIdempotencyStore, RedisPortfolioCache
src/main/resources/db/init/01-schema.sql   # schema + datos semilla (init de Docker)
docker-compose.yml                          # Postgres + Redis (+ app con profile `full`)
Dockerfile                                  # imagen multi-stage (build + runtime, Temurin 21)
postman/                                    # colección + environment de Postman con ejemplos
```
