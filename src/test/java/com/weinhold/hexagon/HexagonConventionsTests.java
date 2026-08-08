package com.weinhold.hexagon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.type.AnnotationMetadata;

import com.weinhold.hexagon.HexagonConventions.Classification;
import com.weinhold.hexagon.HexagonConventions.Stereotype;
import com.weinhold.hexagon.model.Direction;

/** The classification rules themselves, including the ones that are easy to get backwards. */
class HexagonConventionsTests {

    private final HexagonConventions conventions = HexagonConventions.defaults();

    @Test
    void readsAPublisherAsAnAdapterRatherThanAnEvent() {
        // "OrderEventPublisher" contains "Event". Checking the event suffix before the adapter
        // suffixes would file the publisher in the domain core.
        assertThat(classify(OrderEventPublisher.class)).isEqualTo(new Classification(Stereotype.ADAPTER, Direction.SECONDARY));
    }

    @Test
    void treatsAnUndirectedPortPackageAsDriven() {
        // Repositories, gateways and clients outnumber driving ports, and this matches the
        // fallback the annotation pass uses for a bare @Port.
        assertThat(classify(SomePort.class)).isEqualTo(new Classification(Stereotype.PORT, Direction.SECONDARY));
    }

    @Test
    void doesNotClassifyAnInterfaceAsAnAdapterOrAClassAsAPort() {
        // An adapter is something concrete that plugs in; a port is the hole.
        assertThat(classify(OrderController.class).stereotype()).isEqualTo(Stereotype.ADAPTER);
        assertThat(classify(NotAnAdapter.class)).isNull();
    }

    @Test
    void ignoresAbstractTypesAndAnnotations() {
        assertThat(classify(AbstractOrderClient.class)).isNull();
        assertThat(classify(SomeMarker.class)).isNull();
    }

    @Test
    void classifiesNothingWhenDisabled() {
        assertThat(HexagonConventions.disabled()
                                     .classify(OrderController.class.getName(),
                                         AnnotationMetadata.introspect(OrderController.class))).isNull();
    }

    @Test
    void readsTheSimpleNameOfANestedClass() {
        assertThat(HexagonConventions.simpleName("com.acme.Outer$Inner")).isEqualTo("Inner");
        assertThat(HexagonConventions.simpleName("com.acme.Order")).isEqualTo("Order");
    }

    private Classification classify(Class<?> type) {
        return this.conventions.classify(type.getName(), AnnotationMetadata.introspect(type));
    }

    static class OrderEventPublisher {

    }

    static class OrderController {

    }

    /** An interface named like an adapter is still not one. */
    interface NotAnAdapter {

    }

    abstract static class AbstractOrderClient {

    }

    @interface SomeMarker {

    }

    interface SomePort {

    }

}
