package com.weinhold.hexagon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.weinhold.hexagon.model.Provenance;

/**
 * {@code core.events.consumed} — the half of the events block that cannot come from scanning
 * our own packages, because a consumed event is by definition declared somewhere else.
 */
class HexagonConsumedEventsTests {

    private final HexagonScanner scanner =
        new HexagonScanner(List.of("com.weinhold.hexagon.listener"), getClass().getClassLoader(), HexagonConventions.defaults());

    @Test
    void findsEventsTakenByListenerMethods() {
        var events = this.scanner.scan().core().events();

        assertThat(events.consumed()).singleElement().satisfies(event -> {
            assertThat(event.id()).isEqualTo("com.weinhold.hexagon.external.ShipmentDispatched");
            assertThat(event.name()).isEqualTo("ShipmentDispatched");
            // Inspecting live listener methods is neither an annotation on the type nor a
            // package convention, and the contract has a word for exactly that.
            assertThat(event.provenance()).isEqualTo(Provenance.RUNTIME);
        });
    }

    @Test
    void ignoresListenerParametersThatAreNotEvents() {
        var events = this.scanner.scan().core().events();

        assertThat(events.consumed()).extracting(component -> component.id()).doesNotContain("java.lang.String");
    }

    @Test
    void reportsNothingWhenTheServiceListensToNothing() {
        var scan = new HexagonScanner(List.of("com.weinhold.hexagon.conventions"), getClass().getClassLoader(),
            HexagonConventions.defaults()).scan();

        assertThat(scan.core().events().consumed()).isEmpty();
    }

}
