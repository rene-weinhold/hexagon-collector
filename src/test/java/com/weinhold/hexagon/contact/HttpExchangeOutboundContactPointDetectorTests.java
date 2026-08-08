package com.weinhold.hexagon.contact;

import java.util.List;
import java.util.Map;

import com.weinhold.hexagon.contact.ContactPointDetector.AdapterContext;
import com.weinhold.hexagon.contact.ContactPointDetector.Contribution;
import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.Confidence;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.Direction;
import com.weinhold.hexagon.model.Provenance;
import com.weinhold.hexagon.model.Resolution;
import org.junit.jupiter.api.Test;

import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeOutboundContactPointDetectorTests {

	@Test
	void reportsUnresolvedOutboundCallsWhenNoTargetMapped() {
		Contribution contribution = new HttpExchangeOutboundContactPointDetector(new MockEnvironment(), Map.of())
				.detect(context(InventoryClient.class));

		assertThat(contribution.technology()).isEqualTo("spring-http-interface");
		ContactPointInfo reserve = byKey(contribution, "http:POST /api/items/{sku}/reservations");
		assertThat(reserve.direction()).isEqualTo(ContactDirection.OUTBOUND);
		assertThat(reserve.confidence()).isEqualTo(Confidence.MEDIUM);
		assertThat(reserve.target()).isNull();

		assertThat(contribution.contactPoints()).extracting(ContactPointInfo::key)
				.contains("http:GET /api/items/{sku}", "http:POST /api/items/{sku}/reservations");
	}

	@Test
	void resolvesTargetServiceFromConfig() {
		Map<String, String> targets = Map.of(InventoryClient.class.getName(), "inventory-service");

		Contribution contribution = new HttpExchangeOutboundContactPointDetector(new MockEnvironment(), targets)
				.detect(context(InventoryClient.class));

		ContactPointInfo reserve = byKey(contribution,
				"http:inventory-service:POST /api/items/{sku}/reservations");
		assertThat(reserve.confidence()).isEqualTo(Confidence.HIGH);
		assertThat(reserve.direction()).isEqualTo(ContactDirection.OUTBOUND);
		assertThat(reserve.target().logicalService()).isEqualTo("inventory-service");
		assertThat(reserve.target().resolution()).isEqualTo(Resolution.CONFIG);
	}

	@Test
	void downgradesConfidenceWhenThePathCannotBeResolved() {
		// HELP.md promised this and only Kafka/AMQP were doing it: a key containing a literal
		// ${...} will never match anything in the collector and must not claim to be HIGH.
		Contribution contribution = new HttpExchangeOutboundContactPointDetector(new MockEnvironment(),
				Map.of(TemplatedClient.class.getName(), "inventory-service")).detect(context(TemplatedClient.class));

		assertThat(contribution.contactPoints()).singleElement()
				.satisfies(point -> assertThat(point.confidence()).isEqualTo(Confidence.MEDIUM));
	}

	@Test
	void ignoresAdaptersWithoutHttpExchange() {
		Contribution contribution = new HttpExchangeOutboundContactPointDetector(new MockEnvironment(), Map.of())
				.detect(context(NotAClient.class));

		assertThat(contribution.isEmpty()).isTrue();
	}

	private static ContactPointInfo byKey(Contribution contribution, String key) {
		return contribution.contactPoints().stream().filter(point -> point.key().equals(key)).findFirst().orElseThrow();
	}

	private static AdapterContext context(Class<?> type) {
		AdapterInfo base = new AdapterInfo(type.getName(), type.getSimpleName(), Direction.SECONDARY, null,
				Provenance.ANNOTATION, List.of(), List.of());
		return new AdapterContext(type, base);
	}

	@HttpExchange("/api/items")
	interface InventoryClient {

		@GetExchange("/{sku}")
		String get(String sku);

		@PostExchange("/{sku}/reservations")
		String reserve(String sku);

	}

	interface NotAClient {

		String doThing();

	}

	@HttpExchange("/api/items")
	interface TemplatedClient {

		@GetExchange("/${inventory.path}")
		String get();

	}

}
