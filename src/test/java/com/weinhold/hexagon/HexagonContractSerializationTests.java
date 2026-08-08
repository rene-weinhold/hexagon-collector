package com.weinhold.hexagon;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.ComponentInfo;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.CoreInfo;
import com.weinhold.hexagon.model.Direction;
import com.weinhold.hexagon.model.EventsInfo;
import com.weinhold.hexagon.model.HexagonDescriptor;
import com.weinhold.hexagon.model.PortInfo;
import com.weinhold.hexagon.model.Protocol;
import com.weinhold.hexagon.model.Provenance;
import com.weinhold.hexagon.model.ServiceInfo;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the descriptor serializes to the JSON shape defined in the contract, including
 * that {@code generatedAt} is an ISO-8601 instant and that empty optional fields are omitted.
 */
class HexagonContractSerializationTests {

	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	@Test
	void serializesToContractShape() throws Exception {
		CoreInfo core = new CoreInfo("com.acme.orders.domain",
				List.of(new ComponentInfo("com.acme.orders.domain.Order", "Order", Provenance.ANNOTATION)),
				new EventsInfo(
						List.of(new ComponentInfo("com.acme.orders.domain.event.OrderPlaced", "OrderPlaced",
								Provenance.ANNOTATION)),
						List.of()));
		HexagonDescriptor descriptor = new HexagonDescriptor(HexagonDescriptor.SCHEMA_VERSION,
				Instant.parse("2026-08-07T09:14:22Z"),
				new ServiceInfo("orders-service", "Orders", "3.4.1", "prod", null, "com.acme.orders", null), core,
				List.of(new PortInfo("com.acme.orders.application.port.in.PlaceOrderUseCase", "PlaceOrder",
						Direction.PRIMARY, Provenance.ANNOTATION, List.of("placeOrder"))),
				List.of(new AdapterInfo("com.acme.orders.adapter.in.web.OrderController", "Order REST API",
						Direction.PRIMARY, "spring-web", Provenance.ANNOTATION,
						List.of("com.acme.orders.application.port.in.PlaceOrderUseCase"),
						List.of(new ContactPointInfo("http:POST /api/orders", Protocol.HTTP, ContactDirection.INBOUND,
								com.weinhold.hexagon.model.Confidence.HIGH, null,
								java.util.Map.of("method", "POST", "pathTemplate", "/api/orders"))))));

		String json = this.objectMapper.writeValueAsString(descriptor);
		DocumentContext ctx = JsonPath.parse(json);

		assertThat((String) ctx.read("$.schemaVersion")).isEqualTo("1.0.0");
		assertThat((String) ctx.read("$.generatedAt")).isEqualTo("2026-08-07T09:14:22Z");
		assertThat((String) ctx.read("$.service.id")).isEqualTo("orders-service");
		assertThat((String) ctx.read("$.core.basePackage")).isEqualTo("com.acme.orders.domain");
		assertThat((String) ctx.read("$.core.aggregates[0].id")).isEqualTo("com.acme.orders.domain.Order");
		assertThat((String) ctx.read("$.core.events.published[0].name")).isEqualTo("OrderPlaced");
		assertThat((String) ctx.read("$.ports[0].id"))
				.isEqualTo("com.acme.orders.application.port.in.PlaceOrderUseCase");
		assertThat((String) ctx.read("$.ports[0].direction")).isEqualTo("PRIMARY");
		assertThat((String) ctx.read("$.adapters[0].implementsPorts[0]"))
				.isEqualTo("com.acme.orders.application.port.in.PlaceOrderUseCase");
		assertThat((String) ctx.read("$.adapters[0].technology")).isEqualTo("spring-web");
		assertThat((String) ctx.read("$.adapters[0].contactPoints[0].key")).isEqualTo("http:POST /api/orders");
		assertThat((String) ctx.read("$.adapters[0].contactPoints[0].protocol")).isEqualTo("HTTP");
		assertThat((String) ctx.read("$.adapters[0].contactPoints[0].direction")).isEqualTo("INBOUND");
		assertThat((String) ctx.read("$.adapters[0].contactPoints[0].confidence")).isEqualTo("HIGH");
		assertThat((String) ctx.read("$.adapters[0].contactPoints[0].attributes.pathTemplate"))
				.isEqualTo("/api/orders");

		// NON_EMPTY: absent optional fields (incl. empty consumed events and unset target) must not appear.
		assertThat(json).doesNotContain("instanceId").doesNotContain("consumed")
				.doesNotContain("\"target\"").doesNotContain("null");
	}

}
