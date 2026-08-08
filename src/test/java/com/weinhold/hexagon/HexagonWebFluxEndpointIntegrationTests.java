package com.weinhold.hexagon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;

/**
 * The same routes on the reactive stack. A WebFlux service used to report adapters with no
 * contact points at all and simply vanish from the inbound half of the landscape — and the
 * keys have to come out byte-identical to the servlet stack's, or a reactive service and the
 * servlet service calling it will never match up in the collector.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = { "spring.main.web-application-type=reactive", "spring.application.name=orders-service",
        "hexagon.collection.base-packages=com.weinhold.hexagon.reactive" })
class HexagonWebFluxEndpointIntegrationTests {

    @Autowired
    private HexagonEndpoint endpoint;

    @Test
    void attachesReactiveRoutesToTheControllerAdapter() {
        AdapterInfo controller =
            this.endpoint.hexagon()
                         .adapters()
                         .stream()
                         .filter(adapter -> adapter.id().equals("com.weinhold.hexagon.reactive.ReactiveOrdersController"))
                         .findFirst()
                         .orElseThrow();

        assertThat(controller.technology()).isEqualTo("spring-webflux");
        assertThat(controller.contactPoints()).extracting(ContactPointInfo::key)
                                              .containsExactlyInAnyOrder("http:POST /api/orders", "http:GET /api/orders/{id}");
        assertThat(controller.contactPoints()).allSatisfy(
            point -> assertThat(point.direction()).isEqualTo(ContactDirection.INBOUND));
    }

    /** Scoped to one package: the servlet fixtures map the same routes and would collide. */
    @SpringBootApplication(scanBasePackages = "com.weinhold.hexagon.reactive")
    static class TestApplication {

    }

}
