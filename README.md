# Hexagon Collection Starter

A Spring Boot starter that adds a `/actuator/hexagon` endpoint. At startup it works out the
application's **ports and adapters** and serves a structural description of them, following the
contract in [`actuator-hexagon-schema.md`](actuator-hexagon-schema.md).

This is a **library**, not a runnable application — add it as a dependency of a Spring Boot
service.

## Usage

Add the dependency to your consuming project:

```xml

<dependency>
    <groupId>com.weinhold</groupId>
    <artifactId>hexagon-collection-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Then expose the endpoint (Actuator hides everything but `health` by default):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,hexagon
```

`GET /actuator/hexagon` now returns:

```json
{
    "schemaVersion": "1.0.0",
    "generatedAt": "2026-08-07T09:14:22Z",
    "service": {
        "id": "orders-service",
        "basePackage": "com.acme.orders"
    },
    "core": {
        "basePackage": "com.acme.orders.domain",
        "aggregates": [
            {
                "id": "...Order",
                "name": "Order",
                "provenance": "ANNOTATION"
            }
        ],
        "events": {
            "published": [
                {
                    "id": "...OrderPlaced",
                    "name": "OrderPlaced"
                }
            ]
        }
    },
    "ports": [
        {
            "id": "...PlaceOrderUseCase",
            "name": "PlaceOrder",
            "direction": "PRIMARY",
            "provenance": "ANNOTATION",
            "operations": [
                "placeOrder"
            ]
        }
    ],
    "adapters": [
        {
            "id": "...OrderController",
            "name": "Order REST API",
            "direction": "PRIMARY",
            "technology": "spring-web",
            "provenance": "ANNOTATION",
            "implementsPorts": [
                "...PlaceOrderUseCase"
            ],
            "contactPoints": [
                {
                    "key": "http:POST /api/orders",
                    "protocol": "HTTP",
                    "direction": "INBOUND",
                    "confidence": "HIGH",
                    "attributes": {
                        "method": "POST",
                        "pathTemplate": "/api/orders"
                    }
                }
            ]
        }
    ]
}
```

## How your service is recognized

Two passes, in this order. **Annotations always win** — a declared fact is never overwritten by
an inference.

### 1. jMolecules annotations → `provenance: ANNOTATION`

```java

@PrimaryPort
interface PlaceOrderUseCase {
    void placeOrder();
}

@SecondaryPort
interface InventoryPort {
    void reserveStock();
}

@PrimaryAdapter
class OrderController implements PlaceOrderUseCase { ...
}

@SecondaryAdapter
class InventoryRestClient implements InventoryPort { ...
}

@AggregateRoot
class Order { ...
}        // -> core.aggregates

@DomainEvent
class OrderPlaced { ...
}  // -> core.events.published
```

A bare `@Port`/`@Adapter` with no primary/secondary variant cannot express direction and
defaults to `SECONDARY`, with a warning.

### 2. Package and class-name conventions → `provenance: CONVENTION`

Whatever the first pass did not claim is classified by convention, so a service with no
annotations at all still describes itself:

| Signal                                                                                          | Result            |
|-------------------------------------------------------------------------------------------------|-------------------|
| interface in a `port`/`ports` package                                                           | port              |
| concrete class in an `adapter`/`adapters` package                                               | adapter           |
| neighbouring `in`/`inbound`/`primary`/`driving` segment                                         | `PRIMARY`         |
| neighbouring `out`/`outbound`/`secondary`/`driven` segment                                      | `SECONDARY`       |
| interface named `*UseCase`                                                                      | primary port      |
| interface named `*Port`, `*Gateway`, `*Repository`                                              | secondary port    |
| class named `*Controller`, `*Resource`, `*Endpoint`, `*Listener`, `*Consumer`, `*Subscriber`    | primary adapter   |
| class named `*Adapter`, `*Client`, `*Gateway`, `*Repository`, `*Dao`, `*Publisher`, `*Producer` | secondary adapter |
| concrete type named `*Event`, or in an `event`/`events` package                                 | domain event      |

Every suffix list is configurable (see below), and the whole pass can be switched off with
`hexagon.collection.conventions.enabled=false`.

**Aggregates are deliberately not guessed.** Every type in a `domain` package would qualify,
and a core listing value objects and enums as aggregates is worse than an empty one.

### 3. Consumed events → `provenance: RUNTIME`

`core.events.consumed` cannot come from scanning your own packages — a consumed event is by
definition somebody else's published event. Instead the payload types of `@EventListener`,
`@TransactionalEventListener`, `@KafkaListener` and `@RabbitListener` methods are inspected. A
payload counts as an event if it carries `@DomainEvent` or matches an event name convention.

## Contact points

`contactPoints` are discovered by per-technology `ContactPointDetector` beans, each gated so
only the technologies actually present contribute:

| Detector                    | Finds                                                                               |
|-----------------------------|-------------------------------------------------------------------------------------|
| **HTTP inbound (servlet)**  | resolved routes from Spring MVC's `RequestMappingHandlerMapping`                    |
| **HTTP inbound (reactive)** | the same, from WebFlux's `RequestMappingHandlerMapping`                             |
| **HTTP outbound**           | declarative `@HttpExchange` interface clients                                       |
| **Feign**                   | `@FeignClient` interfaces — the target service comes from the annotation            |
| **Kafka**                   | `@KafkaListener` topics (INBOUND) and `@SendTo` (OUTBOUND)                          |
| **AMQP**                    | `@RabbitListener` bindings (INBOUND) and `@SendTo` (OUTBOUND)                       |
| **JDBC**                    | `spring.datasource.url` (or `DataSource` metadata) attached to persistence adapters |

### Confidence is earned, not assumed

Every unknown that goes into a key costs one step of `confidence`, so a key assembled from
several guesses cannot come out looking like a fact:

* an unresolvable `${...}` placeholder in a topic, exchange or path
* a route with no HTTP method condition (the key can only wildcard)
* an outbound call whose target service is unknown, leaving the collector to match by path
* a persistence adapter recognized only by its class name

That last one matters most: two services reporting the same `jdbc:` key is how the collector
finds a shared database, and a naming convention must not assert one at `HIGH`.

### Nothing secret leaves

Contact points are normalized: JDBC host, port, credentials and connection properties are
dropped, query strings are stripped from paths, and path variables stay as `{name}` templates.
The endpoint may be exposed, so it may only contain what could also appear in an architecture
diagram.

## Configuration

Properties are bound under the `hexagon.collection` prefix:

```yaml
hexagon:
  collection:
    enabled: true                      # set false to disable the endpoint entirely
    base-packages: [ com.acme.orders ]   # defaults to the app's auto-configuration packages
    service:
      display-name: Orders
      version: 3.4.1                   # defaults to build-info version when present
      environment: prod
      instance-id: orders-7d9f4c-x2k   # defaults to spring.application.instance-id, then $HOSTNAME
      repository: https://github.com/acme/orders-service
    targets: # resolve outbound HTTP adapters to logical services
      com.acme.orders.adapter.out.inventory.InventoryClient: inventory-service
    conventions:
      enabled: true
      primary-adapter-suffixes: [ Controller, Resource, Facade ]
      secondary-adapter-suffixes: [ Client, Publisher, Repository ]
      primary-port-suffixes: [ UseCase ]
      secondary-port-suffixes: [ Port, Gateway, Repository ]
      event-suffixes: [ Event ]
```

`targets` maps an outbound adapter (by fully-qualified class name) to the logical service it
calls. Declarative `@HttpExchange` clients carry no host, so this is how their outbound contact
points become target-resolved (`http:{service}:...`) instead of left for the collector. For
`@FeignClient` it is only needed to *override* the name the annotation already carries.

## Native images

The endpoint works after GraalVM native compilation. Runtime classpath scanning cannot work
there — a native image has no classpath to read — so Spring's AOT processing runs the scan on
the JVM before compilation and writes the answer to `META-INF/hexagon/components.idx`, together
with the reflection hints needed to read those types back. Where the index is present it is
authoritative and considerably faster than scanning; on the JVM it is simply absent and nothing
changes.

Consequence: a native build bakes in the base packages and conventions configured at build
time, as AOT always does.

## Adding a contact-point detector

Detectors are the extension point for new technologies. Implement
`com.weinhold.hexagon.contact.ContactPointDetector`, build keys with `CanonicalKey`, resolve
placeholders through `Placeholders`, and register the detector as a bean guarded by
`@ConditionalOnClass`. Give it an `@Order` — the first detector to contribute names the
adapter's `technology`. The factory runs every detector against every adapter and merges the
results, deduplicated by key *and* direction.

## Scope & current limitations

Detection is deliberately limited to **declarative** signals — imperative calls carry no static
metadata and are not guessed:

* Imperative `RestClient`/`WebClient`, `KafkaTemplate.send(...)` and `RabbitTemplate` sends are
  invisible unless expressed declaratively (`@HttpExchange`, `@FeignClient`, `@SendTo`).
* gRPC and file-based contact points have no detector yet, though the contract has the
  protocols.
* The descriptor is computed **once, lazily on first read**, and cached — so detectors see
  fully-initialized framework beans, while still honouring "computed once, served afterwards".
  It describes structure, not runtime metrics.

## Building

```bash
./mvnw install                 # build and install locally
./mvnw -Prelease deploy        # with sources, javadoc and signatures
```

## Reference documentation

* [jMolecules hexagonal architecture](https://github.com/xmolecules/jmolecules)
* [Creating Your Own Starter](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)
* [Spring Boot Actuator endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)

## Licence

[Apache License 2.0](LICENSE).
