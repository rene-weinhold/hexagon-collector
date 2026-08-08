package com.weinhold.hexagon.contact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CanonicalKeyTests {

    @Test
    void httpInboundUppercasesMethodAndKeepsPathTemplate() {
        assertThat(CanonicalKey.httpInbound("post", "/api/orders")).isEqualTo("http:POST /api/orders");
        assertThat(CanonicalKey.httpInbound("GET", "/api/orders/{id}")).isEqualTo("http:GET /api/orders/{id}");
    }

    @Test
    void httpNormalizesPath() {
        assertThat(CanonicalKey.httpInbound("GET", "api/orders/")).isEqualTo("http:GET /api/orders");
        assertThat(CanonicalKey.httpInbound("GET", "/api/orders?page=1")).isEqualTo("http:GET /api/orders");
    }

    @Test
    void otherProtocols() {
        assertThat(CanonicalKey.httpOutbound("inventory-service", "GET", "/api/items/{sku}")).isEqualTo(
            "http:inventory-service:GET /api/items/{sku}");
        assertThat(CanonicalKey.kafkaTopic("orders.placed")).isEqualTo("kafka:topic/orders.placed");
        assertThat(CanonicalKey.amqpExchange("billing")).isEqualTo("amqp:exchange/billing");
        assertThat(CanonicalKey.jdbc("postgresql", "orders")).isEqualTo("jdbc:postgresql/orders");
        assertThat(CanonicalKey.jdbc("postgresql", null)).isEqualTo("jdbc:postgresql");
    }

}
