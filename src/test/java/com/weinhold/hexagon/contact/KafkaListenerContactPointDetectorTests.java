package com.weinhold.hexagon.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.mock.env.MockEnvironment;

import com.weinhold.hexagon.contact.ContactPointDetector.AdapterContext;
import com.weinhold.hexagon.contact.ContactPointDetector.Contribution;
import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.Confidence;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.Direction;
import com.weinhold.hexagon.model.Provenance;

class KafkaListenerContactPointDetectorTests {

    @Test
    void reportsConsumedTopicsAsInboundContactPoints() {
        Contribution contribution =
            new KafkaListenerContactPointDetector(new MockEnvironment()).detect(context(OrderEventsListener.class));

        assertThat(contribution.technology()).isEqualTo("spring-kafka");
        assertThat(contribution.contactPoints()).singleElement().satisfies(point -> {
            assertThat(point.key()).isEqualTo("kafka:topic/orders.placed");
            assertThat(point.direction()).isEqualTo(ContactDirection.INBOUND);
            assertThat(point.confidence()).isEqualTo(Confidence.HIGH);
            assertThat(point.attributes()).containsEntry("topic", "orders.placed");
        });
    }

    @Test
    void resolvesTopicPlaceholders() {
        MockEnvironment environment = new MockEnvironment().withProperty("orders.topic", "orders.placed");

        Contribution contribution = new KafkaListenerContactPointDetector(environment).detect(context(PlaceholderListener.class));

        assertThat(contribution.contactPoints()).extracting(ContactPointInfo::key).containsExactly("kafka:topic/orders.placed");
    }

    @Test
    void downgradesConfidenceForUnresolvedPlaceholders() {
        Contribution contribution =
            new KafkaListenerContactPointDetector(new MockEnvironment()).detect(context(PlaceholderListener.class));

        assertThat(contribution.contactPoints()).singleElement()
                                                .satisfies(point -> assertThat(point.confidence()).isEqualTo(Confidence.MEDIUM));
    }

    @Test
    void reportsSendToTopicsAsOutboundContactPoints() {
        Contribution contribution =
            new KafkaListenerContactPointDetector(new MockEnvironment()).detect(context(ForwardingListener.class));

        assertThat(contribution.contactPoints()).anySatisfy(point -> {
            assertThat(point.key()).isEqualTo("kafka:topic/orders.enriched");
            assertThat(point.direction()).isEqualTo(ContactDirection.OUTBOUND);
            assertThat(point.confidence()).isEqualTo(Confidence.HIGH);
        }).anySatisfy(point -> {
            assertThat(point.key()).isEqualTo("kafka:topic/orders.placed");
            assertThat(point.direction()).isEqualTo(ContactDirection.INBOUND);
        });
    }

    @Test
    void doesNotReportSendToWithoutAKafkaListener() {
        Contribution contribution =
            new KafkaListenerContactPointDetector(new MockEnvironment()).detect(context(BareForwarder.class));

        assertThat(contribution.isEmpty()).isTrue();
    }

    private static AdapterContext context(Class<?> type) {
        AdapterInfo base = new AdapterInfo(type.getName(), type.getSimpleName(), Direction.PRIMARY, null, Provenance.ANNOTATION,
            List.of(), List.of());
        return new AdapterContext(type, base);
    }

    static class OrderEventsListener {

        @KafkaListener(topics = "orders.placed")
        void onOrderPlaced(String message) {
        }

    }

    static class ForwardingListener {

        @KafkaListener(topics = "orders.placed")
        @SendTo("orders.enriched")
        String onOrderPlaced(String message) {
            return message;
        }

    }

    static class BareForwarder {

        @SendTo("orders.enriched")
        String forward(String message) {
            return message;
        }

    }

    static class PlaceholderListener {

        @KafkaListener(topics = "${orders.topic}")
        void onOrderPlaced(String message) {
        }

    }

}
