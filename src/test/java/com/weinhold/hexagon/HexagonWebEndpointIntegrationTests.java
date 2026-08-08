package com.weinhold.hexagon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.HexagonDescriptor;
import com.weinhold.hexagon.model.Protocol;

/**
 * Boots a mock servlet web context so the HTTP-inbound detector reads real routes from the
 * live {@code RequestMappingHandlerMapping} and attaches them to the controller adapter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = { "spring.application.name=orders-service", "hexagon.collection.base-packages=com.weinhold.hexagon.web" })
class HexagonWebEndpointIntegrationTests {

    @Autowired
    private HexagonEndpoint endpoint;

    @Test
    void attachesHttpInboundContactPointsToTheControllerAdapter() {
        HexagonDescriptor descriptor = this.endpoint.hexagon();

        AdapterInfo controller = descriptor.adapters()
                                           .stream()
                                           .filter(adapter -> adapter.id().equals("com.weinhold.hexagon.web.OrdersApiController"))
                                           .findFirst()
                                           .orElseThrow();

        assertThat(controller.technology()).isEqualTo("spring-web");
        assertThat(controller.contactPoints()).extracting(ContactPointInfo::key)
                                              .contains("http:POST /api/orders", "http:GET /api/orders/{id}");
        assertThat(controller.contactPoints()).allSatisfy(point -> {
            assertThat(point.protocol()).isEqualTo(Protocol.HTTP);
            assertThat(point.direction()).isEqualTo(ContactDirection.INBOUND);
        });
    }

    /** Scoped to one package: the reactive fixtures map the same routes and would collide. */
    @SpringBootApplication(scanBasePackages = "com.weinhold.hexagon.web")
    static class TestApplication {
    }

}
