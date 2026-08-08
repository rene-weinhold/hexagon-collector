package com.weinhold.hexagon.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.mock.env.MockEnvironment;

import com.weinhold.hexagon.contact.ContactPointDetector.AdapterContext;
import com.weinhold.hexagon.contact.ContactPointDetector.Contribution;
import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.Direction;
import com.weinhold.hexagon.model.Provenance;

class AmqpRabbitContactPointDetectorTests {

    @Test
    void reportsBoundExchangeAsInboundAndSendToAsOutbound() {
        Contribution contribution =
            new AmqpRabbitContactPointDetector(new MockEnvironment()).detect(context(ShipmentListener.class));

        assertThat(contribution.technology()).isEqualTo("spring-amqp");
        assertThat(contribution.contactPoints()).anySatisfy(point -> {
            assertThat(point.key()).isEqualTo("amqp:exchange/shipping");
            assertThat(point.direction()).isEqualTo(ContactDirection.INBOUND);
        }).anySatisfy(point -> {
            assertThat(point.key()).isEqualTo("amqp:exchange/billing");
            assertThat(point.direction()).isEqualTo(ContactDirection.OUTBOUND);
        });
    }

    @Test
    void reportsPlainQueueListenerAsQueueContactPoint() {
        Contribution contribution =
            new AmqpRabbitContactPointDetector(new MockEnvironment()).detect(context(QueueListener.class));

        assertThat(contribution.contactPoints()).extracting(ContactPointInfo::key).containsExactly("amqp:queue/orders.inbound");
    }

    @Test
    void ignoresAdaptersWithoutRabbitListener() {
        Contribution contribution = new AmqpRabbitContactPointDetector(new MockEnvironment()).detect(context(NotAListener.class));

        assertThat(contribution.isEmpty()).isTrue();
    }

    private static AdapterContext context(Class<?> type) {
        AdapterInfo base = new AdapterInfo(type.getName(), type.getSimpleName(), Direction.SECONDARY, null, Provenance.ANNOTATION,
            List.of(), List.of());
        return new AdapterContext(type, base);
    }

    static class ShipmentListener {

        @RabbitListener(bindings = @QueueBinding(value = @Queue("shipment.queue"), exchange = @Exchange("shipping"),
            key = "shipment.dispatched"))
        @SendTo("billing/invoice.requested")
        String onShipment(String message) {
            return message;
        }

    }

    static class QueueListener {

        @RabbitListener(queues = "orders.inbound")
        void onOrder(String message) {
        }

    }

    static class NotAListener {

        void handle(String message) {
        }

    }

}
