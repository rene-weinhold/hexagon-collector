package com.weinhold.hexagon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.CoreInfo;
import com.weinhold.hexagon.model.Direction;
import com.weinhold.hexagon.model.PortInfo;
import com.weinhold.hexagon.model.Provenance;

class HexagonScannerTests {

    private final HexagonScanner scanner =
        new HexagonScanner(List.of("com.weinhold.hexagon.sample"), getClass().getClassLoader());

    @Test
    void discoversPortsWithDirectionAndOperations() {
        List<PortInfo> ports = this.scanner.scan().ports();

        assertThat(ports).hasSize(2);

        PortInfo placeOrder = findById(ports, PortInfo::id, "com.weinhold.hexagon.sample.PlaceOrderUseCase");
        assertThat(placeOrder.direction()).isEqualTo(Direction.PRIMARY);
        assertThat(placeOrder.name()).isEqualTo("PlaceOrder");
        assertThat(placeOrder.provenance()).isEqualTo(Provenance.ANNOTATION);
        assertThat(placeOrder.operations()).containsExactly("placeOrder");

        PortInfo inventory = findById(ports, PortInfo::id, "com.weinhold.hexagon.sample.InventoryPort");
        assertThat(inventory.direction()).isEqualTo(Direction.SECONDARY);
        assertThat(inventory.name()).isEqualTo("InventoryPort");
        assertThat(inventory.operations()).containsExactly("releaseStock", "reserveStock");
    }

    @Test
    void discoversAdaptersAndLinksImplementedPorts() {
        List<AdapterInfo> adapters = this.scanner.scan().adapters();

        assertThat(adapters).hasSize(2);

        AdapterInfo controller = findById(adapters, AdapterInfo::id, "com.weinhold.hexagon.sample.OrderController");
        assertThat(controller.direction()).isEqualTo(Direction.PRIMARY);
        assertThat(controller.name()).isEqualTo("Order REST API");
        assertThat(controller.implementsPorts()).containsExactly("com.weinhold.hexagon.sample.PlaceOrderUseCase");

        AdapterInfo client = findById(adapters, AdapterInfo::id, "com.weinhold.hexagon.sample.InventoryRestClient");
        assertThat(client.direction()).isEqualTo(Direction.SECONDARY);
        assertThat(client.implementsPorts()).containsExactly("com.weinhold.hexagon.sample.InventoryPort");
    }

    @Test
    void discoversCoreAggregatesAndDomainEvents() {
        CoreInfo core = this.scanner.scan().core();

        assertThat(core).isNotNull();
        assertThat(core.basePackage()).isEqualTo("com.weinhold.hexagon.sample.domain");

        assertThat(core.aggregates()).singleElement().satisfies(aggregate -> {
            assertThat(aggregate.id()).isEqualTo("com.weinhold.hexagon.sample.domain.Order");
            assertThat(aggregate.name()).isEqualTo("Order");
            assertThat(aggregate.provenance()).isEqualTo(Provenance.ANNOTATION);
        });

        assertThat(core.events().consumed()).isEmpty();
        assertThat(core.events().published()).singleElement().satisfies(event -> {
            assertThat(event.id()).isEqualTo("com.weinhold.hexagon.sample.domain.event.OrderPlaced");
            assertThat(event.name()).isEqualTo("OrderPlaced");
        });
    }

    private static <T> T findById(List<T> items, java.util.function.Function<T, String> id, String value) {
        return items.stream().filter(item -> value.equals(id.apply(item))).findFirst().orElseThrow();
    }

}
