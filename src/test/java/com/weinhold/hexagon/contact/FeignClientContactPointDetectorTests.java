package com.weinhold.hexagon.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.weinhold.hexagon.contact.ContactPointDetector.AdapterContext;
import com.weinhold.hexagon.contact.ContactPointDetector.Contribution;
import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.Confidence;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.Direction;
import com.weinhold.hexagon.model.Provenance;
import com.weinhold.hexagon.model.Resolution;

class FeignClientContactPointDetectorTests {

    @Test
    void takesTheTargetServiceFromTheAnnotation() {
        Contribution contribution =
            new FeignClientContactPointDetector(new MockEnvironment(), Map.of()).detect(context(InventoryClient.class));

        assertThat(contribution.technology()).isEqualTo("spring-cloud-openfeign");

        ContactPointInfo reserve = byKey(contribution, "http:inventory-service:POST /api/items/{sku}/reservations");
        assertThat(reserve.direction()).isEqualTo(ContactDirection.OUTBOUND);
        assertThat(reserve.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(reserve.target().logicalService()).isEqualTo("inventory-service");
        // Feign names its target itself; that name is a discovery id, not a configured guess.
        assertThat(reserve.target().resolution()).isEqualTo(Resolution.SERVICE_DISCOVERY);
        assertThat(reserve.attributes()).containsEntry("method", "POST")
                                        .containsEntry("pathTemplate", "/api/items/{sku}/reservations");

        assertThat(contribution.contactPoints()).extracting(ContactPointInfo::key)
                                                .contains("http:inventory-service:GET /api/items/{sku}");
    }

    @Test
    void marksAFixedUrlAsDeclaredRatherThanDiscovered() {
        Contribution contribution =
            new FeignClientContactPointDetector(new MockEnvironment(), Map.of()).detect(context(LegacyClient.class));

        assertThat(contribution.contactPoints()).singleElement()
                                                .satisfies(point -> assertThat(point.target().resolution()).isEqualTo(
                                                    Resolution.ANNOTATION));
    }

    @Test
    void letsConfiguredTargetsOverrideTheAnnotation() {
        Contribution contribution = new FeignClientContactPointDetector(new MockEnvironment(),
            Map.of(InventoryClient.class.getName(), "inventory-v2")).detect(context(InventoryClient.class));

        assertThat(contribution.contactPoints()).allSatisfy(point -> {
            assertThat(point.target().logicalService()).isEqualTo("inventory-v2");
            assertThat(point.target().resolution()).isEqualTo(Resolution.CONFIG);
        });
    }

    @Test
    void resolvesPlaceholdersInTheServiceName() {
        MockEnvironment environment = new MockEnvironment().withProperty("inventory.service", "inventory-service");

        Contribution contribution =
            new FeignClientContactPointDetector(environment, Map.of()).detect(context(TemplatedClient.class));

        assertThat(contribution.contactPoints()).singleElement()
                                                .satisfies(point -> assertThat(point.key()).isEqualTo(
                                                    "http:inventory-service:GET /api/items"));
    }

    @Test
    void ignoresAdaptersThatAreNotFeignClients() {
        Contribution contribution =
            new FeignClientContactPointDetector(new MockEnvironment(), Map.of()).detect(context(NotAClient.class));

        assertThat(contribution.isEmpty()).isTrue();
    }

    private static ContactPointInfo byKey(Contribution contribution, String key) {
        return contribution.contactPoints().stream().filter(point -> point.key().equals(key)).findFirst().orElseThrow();
    }

    private static AdapterContext context(Class<?> type) {
        AdapterInfo base = new AdapterInfo(type.getName(), type.getSimpleName(), Direction.SECONDARY, null, Provenance.ANNOTATION,
            List.of(), List.of());
        return new AdapterContext(type, base);
    }

    @FeignClient(name = "inventory-service", path = "/api/items")
    interface InventoryClient {

        @GetMapping("/{sku}")
        String get(String sku);

        @PostMapping("/{sku}/reservations")
        String reserve(String sku);

    }

    @FeignClient(name = "legacy-billing", url = "https://billing.internal")
    interface LegacyClient {

        @GetMapping("/invoices")
        String invoices();

    }

    @FeignClient(name = "${inventory.service}", path = "/api/items")
    interface TemplatedClient {

        @GetMapping
        String all();

    }

    interface NotAClient {

        String doThing();

    }

}
