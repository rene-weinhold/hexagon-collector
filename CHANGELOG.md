# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Note that the artifact version and the endpoint's `schemaVersion` move independently: the
schema is the contract with the collector and changes far less often than the code that
produces it.

## [0.1.0] — 2026-08-08

First release. Serves `schemaVersion` 1.0.0 of the
[endpoint contract](actuator-hexagon-schema.md).

### Added

- `/actuator/hexagon`, computed once on first read and served unchanged afterwards.
- **Annotation scanning** for jMolecules `@Port`/`@Adapter` and their primary/secondary
  variants, plus `@AggregateRoot` and `@DomainEvent` — `provenance: ANNOTATION`.
- **Convention scanning** from package layout and class-name suffixes, so a service carrying
  no annotations still describes itself — `provenance: CONVENTION`. Annotations always win.
  Aggregates are deliberately not guessed.
- **Consumed events** from `@EventListener`, `@TransactionalEventListener`, `@KafkaListener`
  and `@RabbitListener` payload types — `provenance: RUNTIME`.
- **Contact-point detectors** for HTTP inbound (servlet and reactive), `@HttpExchange`
  outbound, `@FeignClient` outbound, Kafka, AMQP and JDBC. `@FeignClient` is read by
  annotation name, so the starter carries no Spring Cloud dependency.
- **Confidence downgrading**: every unknown that goes into a canonical key — an unresolved
  placeholder, a missing HTTP method, an unresolved target, a persistence adapter matched
  only by name — costs one step, so a key assembled from guesses cannot look like a fact.
- **Native-image support**: Spring AOT processing writes the scan result to
  `META-INF/hexagon/components.idx` with matching reflection hints, and the index replaces
  classpath scanning wherever it is present.
- Configuration under `hexagon.collection.*`, with generated IDE metadata.

### Known limitations

- Imperative `RestClient`/`WebClient`, `KafkaTemplate.send(...)` and `RabbitTemplate` sends
  carry no static metadata and are not guessed.
- The `GRPC` and `FILE` protocols exist in the contract but have no detector yet.
- The native-image path is covered by unit tests but has not been exercised through a real
  `native-image` build.

[0.1.0]: https://github.com/weinhold/hexagon-collector/releases/tag/v0.1.0
