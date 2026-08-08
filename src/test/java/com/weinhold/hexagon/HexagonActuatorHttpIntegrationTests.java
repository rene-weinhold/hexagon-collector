package com.weinhold.hexagon;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

/**
 * The endpoint as a consumer actually meets it: exposed through Actuator, fetched over real
 * HTTP, serialized by the application's own Jackson setup.
 * <p>Everything else in this suite calls the endpoint bean directly, which leaves the two
 * things most likely to break in production untested — whether {@code hexagon} can be exposed
 * at all, and whether the descriptor survives the actual serializer rather than one the test
 * configured to its own liking.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = { "spring.application.name=orders-service", "hexagon.collection.base-packages=com.weinhold.hexagon.web",
        "hexagon.collection.service.display-name=Orders", "hexagon.collection.service.environment=test",
        "management.endpoints.web.exposure.include=hexagon" })
class HexagonActuatorHttpIntegrationTests {

    @Value("${local.server.port}")
    private int port;

    private String body;

    private DocumentContext json;

    @BeforeEach
    void fetch() throws Exception {
        HttpResponse<String> response =
            HttpClient.newHttpClient()
                      .send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/actuator/hexagon")).build(),
                          HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        this.body = response.body();
        this.json = JsonPath.parse(this.body);
    }

    @Test
    void servesTheContractOverHttp() {
        assertThat((String) this.json.read("$.schemaVersion")).isEqualTo("1.0.0");
        assertThat((String) this.json.read("$.service.id")).isEqualTo("orders-service");
        assertThat((String) this.json.read("$.service.displayName")).isEqualTo("Orders");
        assertThat((String) this.json.read("$.service.environment")).isEqualTo("test");
        // An ISO-8601 instant, not a float of epoch seconds: the collector has to parse this.
        assertThat((String) this.json.read("$.generatedAt")).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");

        assertThat((String) this.json.read("$.adapters[0].id")).isEqualTo("com.weinhold.hexagon.web.OrdersApiController");
        assertThat((String) this.json.read("$.adapters[0].technology")).isEqualTo("spring-web");
        assertThat(this.json.<List<String>> read("$.adapters[0].contactPoints[*].key")).contains("http:POST /api/orders",
            "http:GET /api/orders/{id}");
    }

    @Test
    void wildcardsRoutesThatDeclareNoHttpMethod() {
        assertThat(this.json.<List<String>> read("$.adapters[0].contactPoints[*].key")).contains("http:* /api/orders/search");
        assertThat(this.json.<List<String>> read(
            "$.adapters[0].contactPoints[?(@.key == 'http:* /api/orders/search')].confidence")).containsExactly("MEDIUM");
    }

    @Test
    void omitsEveryFieldItHasNothingToSay() {
        // A service that knows half delivers half — it does not deliver nulls (principle 3).
        assertThat(this.body).doesNotContain("null").doesNotContain("\"target\"").doesNotContain("\"consumed\"");
    }

    /** Scoped to one package: the reactive fixtures map the same routes and would collide. */
    @SpringBootApplication(scanBasePackages = "com.weinhold.hexagon.web")
    static class TestApplication {

    }

}
