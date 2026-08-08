package com.weinhold.hexagon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;

import com.weinhold.hexagon.contact.ContactPointDetector;
import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.Confidence;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.HexagonDescriptor;
import com.weinhold.hexagon.model.Protocol;

class HexagonDescriptorFactoryTests {

    private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

    private final MockEnvironment environment = new MockEnvironment();

    private final HexagonCollectionProperties properties = new HexagonCollectionProperties();

    HexagonDescriptorFactoryTests() {
        this.properties.getBasePackages().add("com.weinhold.hexagon.sample");
    }

    @Test
    void computesOnceAndServesTheSameAnswerAfterwards() {
        HexagonDescriptorFactory factory = factory(List.of());

        HexagonDescriptor first = factory.get();
        HexagonDescriptor second = factory.get();

        // Contract principle 1: structure, computed once and then only served. A second
        // generatedAt would also make every poll look like a change to the collector.
        assertThat(first).isSameAs(second);
    }

    @Test
    void keepsBothDirectionsOfTheSameKey() {
        // A gateway that serves a route and calls the same route upstream has two distinct
        // touchpoints. Collapsing them by key alone deletes one end of an edge.
        HexagonDescriptorFactory factory = factory(List.of(detector(point("http:GET /api/orders", ContactDirection.INBOUND),
            point("http:GET /api/orders", ContactDirection.OUTBOUND), point("http:GET /api/orders", ContactDirection.INBOUND))));

        AdapterInfo adapter = factory.get().adapters().getFirst();

        assertThat(adapter.contactPoints()).hasSize(2)
                                           .extracting(ContactPointInfo::direction)
                                           .containsExactlyInAnyOrder(ContactDirection.INBOUND, ContactDirection.OUTBOUND);
    }

    @Test
    void takesTheInstanceIdFromTheEnvironment() {
        this.environment.setProperty("spring.application.instance-id", "orders-service-7d9f4c-x2k");

        assertThat(factory(List.of()).get().service().instanceId()).isEqualTo("orders-service-7d9f4c-x2k");
    }

    @Test
    void prefersAnExplicitlyConfiguredInstanceId() {
        this.environment.setProperty("spring.application.instance-id", "from-environment");
        this.properties.getService().setInstanceId("from-configuration");

        assertThat(factory(List.of()).get().service().instanceId()).isEqualTo("from-configuration");
    }

    @Test
    void leavesTheInstanceIdOutWhenNothingIdentifiesTheInstance() {
        assertThat(factory(List.of()).get().service().instanceId()).isNull();
    }

    private HexagonDescriptorFactory factory(List<ContactPointDetector> detectors) {
        return new HexagonDescriptorFactory(this.beanFactory, this.environment, this.properties,
            this.beanFactory.getBeanProvider(BuildProperties.class), detectors);
    }

    private static ContactPointDetector detector(ContactPointInfo... points) {
        return adapter -> new ContactPointDetector.Contribution("test", List.of(points));
    }

    private static ContactPointInfo point(String key, ContactDirection direction) {
        return new ContactPointInfo(key, Protocol.HTTP, direction, Confidence.HIGH, null, Map.of());
    }

}
