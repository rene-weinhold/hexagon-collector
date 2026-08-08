package com.weinhold.hexagon.aot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.HexagonDescriptor;
import com.weinhold.hexagon.model.Protocol;

class HexagonRuntimeHintsTests {

    private final RuntimeHints hints = new RuntimeHints();

    HexagonRuntimeHintsTests() {
        new HexagonRuntimeHints().registerHints(this.hints, getClass().getClassLoader());
    }

    @Test
    void registersThePayloadRecordsSoJacksonCanSerializeThem() {
        // Without these the endpoint compiles, starts, and then fails at the moment somebody
        // actually reads it — the worst place to find out.
        assertThat(RuntimeHintsPredicates.reflection().onType(HexagonDescriptor.class)).accepts(this.hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(ContactPointInfo.class)).accepts(this.hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(Protocol.class)).accepts(this.hints);
    }

    @Test
    void registersTheComponentIndexAsAReadableResource() {
        assertThat(RuntimeHintsPredicates.resource().forResource(HexagonComponentIndex.RESOURCE_LOCATION)).accepts(this.hints);
    }

}
