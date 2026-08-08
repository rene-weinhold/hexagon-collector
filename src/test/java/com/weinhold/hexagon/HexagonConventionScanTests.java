package com.weinhold.hexagon;

import java.util.List;

import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.Direction;
import com.weinhold.hexagon.model.PortInfo;
import com.weinhold.hexagon.model.Provenance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The un-annotated service. Design principle 3 of the contract requires the starter to
 * describe a service that carries no jMolecules annotation at all and only follows package
 * conventions — and principle 4 requires everything found that way to be marked as inferred.
 */
class HexagonConventionScanTests {

	private static final String BASE = "com.weinhold.hexagon.conventions";

	private final HexagonScanner scanner = new HexagonScanner(List.of(BASE), getClass().getClassLoader(),
			HexagonConventions.defaults());

	@Test
	void readsPortDirectionFromThePackageLayout() {
		List<PortInfo> ports = this.scanner.scan().ports();

		assertThat(ports).extracting(PortInfo::id).containsExactly(BASE + ".application.port.in.PlaceOrderUseCase",
				BASE + ".application.port.out.OrderRepository");
		assertThat(ports).allSatisfy(port -> assertThat(port.provenance()).isEqualTo(Provenance.CONVENTION));

		assertThat(ports.getFirst().direction()).isEqualTo(Direction.PRIMARY);
		assertThat(ports.getLast().direction()).isEqualTo(Direction.SECONDARY);
	}

	@Test
	void reportsOperationsInheritedFromSuperInterfaces() {
		PortInfo repository = this.scanner.scan().ports().stream()
				.filter(port -> port.id().endsWith("OrderRepository")).findFirst().orElseThrow();

		assertThat(repository.operations()).containsExactly("findById", "save");
	}

	@Test
	void readsAdapterDirectionFromThePackageLayoutAndLinksPorts() {
		List<AdapterInfo> adapters = this.scanner.scan().adapters();

		AdapterInfo controller = adapters.stream().filter(adapter -> adapter.id().endsWith("OrderWebController"))
				.findFirst().orElseThrow();
		assertThat(controller.direction()).isEqualTo(Direction.PRIMARY);
		assertThat(controller.provenance()).isEqualTo(Provenance.CONVENTION);
		assertThat(controller.implementsPorts()).containsExactly(BASE + ".application.port.in.PlaceOrderUseCase");

		AdapterInfo persistence = adapters.stream().filter(adapter -> adapter.id().endsWith("OrderPersistenceAdapter"))
				.findFirst().orElseThrow();
		assertThat(persistence.direction()).isEqualTo(Direction.SECONDARY);
		assertThat(persistence.implementsPorts()).containsExactly(BASE + ".application.port.out.OrderRepository");
	}

	@Test
	void findsEventsButNeverGuessesAggregates() {
		var core = this.scanner.scan().core();

		assertThat(core.events().published()).singleElement().satisfies(event -> {
			assertThat(event.id()).isEqualTo(BASE + ".domain.event.OrderShipped");
			assertThat(event.provenance()).isEqualTo(Provenance.CONVENTION);
		});
		// Money sits in the domain package and is not an event. Listing every such type as an
		// aggregate would fill the core with value objects and enums, which is worse than an
		// empty core: the point of the map is that what it shows can be believed.
		assertThat(core.aggregates()).isEmpty();
	}

	@Test
	void findsNothingWhenConventionsAreTurnedOff() {
		var scan = new HexagonScanner(List.of(BASE), getClass().getClassLoader(), HexagonConventions.disabled()).scan();

		assertThat(scan.ports()).isEmpty();
		assertThat(scan.adapters()).isEmpty();
		assertThat(scan.core()).isNull();
	}

	@Test
	void neverOverwritesAnAnnotationWithAGuess() {
		// The sample package is fully annotated and its classes would also match conventions
		// (OrderController, InventoryRestClient). A declared fact outranks an inference.
		var scan = new HexagonScanner(List.of("com.weinhold.hexagon.sample"), getClass().getClassLoader(),
				HexagonConventions.defaults()).scan();

		assertThat(scan.ports()).hasSize(2);
		assertThat(scan.adapters()).hasSize(2);
		assertThat(scan.ports()).allSatisfy(port -> assertThat(port.provenance()).isEqualTo(Provenance.ANNOTATION));
		assertThat(scan.adapters())
				.allSatisfy(adapter -> assertThat(adapter.provenance()).isEqualTo(Provenance.ANNOTATION));
	}

}
