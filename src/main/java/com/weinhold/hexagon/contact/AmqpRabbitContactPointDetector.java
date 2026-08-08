package com.weinhold.hexagon.contact;

import static com.weinhold.hexagon.model.Confidence.HIGH;
import static com.weinhold.hexagon.model.Confidence.MEDIUM;
import static com.weinhold.hexagon.model.ContactDirection.INBOUND;
import static com.weinhold.hexagon.model.ContactDirection.OUTBOUND;
import static com.weinhold.hexagon.model.Protocol.AMQP;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.util.StringUtils;

import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;

/**
 * Reports AMQP touchpoints for adapters using Spring AMQP. {@code @RabbitListener} bindings
 * yield {@code INBOUND} {@code amqp:exchange/{exchange}} (or {@code amqp:queue/{queue}} when
 * only a queue is known), and {@code @SendTo} on a listener yields {@code OUTBOUND}
 * {@code amqp:exchange/{exchange}}.
 * <p>Imperative {@code RabbitTemplate} sends carry no static metadata and are not reported.
 */
public class AmqpRabbitContactPointDetector implements ContactPointDetector {

    private final Environment environment;

    public AmqpRabbitContactPointDetector(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Contribution detect(AdapterContext adapter) {
        var exchanges = new LinkedHashSet<String>();
        var queues = new LinkedHashSet<String>();
        var hasListener = collectInbound(adapter.type(), exchanges, queues);
        for (var method : adapter.type().getDeclaredMethods()) {
            hasListener |= collectInbound(method, exchanges, queues);
        }
        if (!hasListener) {
            return Contribution.none();
        }

        var outboundExchanges = new LinkedHashSet<String>();
        collectSendTo(adapter.type(), outboundExchanges);
        for (var method : adapter.type().getDeclaredMethods()) {
            collectSendTo(method, outboundExchanges);
        }

        var points = new ArrayList<ContactPointInfo>();
        exchanges.forEach(exchange -> points.add(point(CanonicalKey.amqpExchange(resolve(exchange)), exchange, INBOUND)));
        queues.forEach(queue -> points.add(point(CanonicalKey.amqpQueue(resolve(queue)), queue, INBOUND)));
        outboundExchanges
            .forEach(exchange -> points.add(point(CanonicalKey.amqpExchange(resolve(exchange)), exchange, OUTBOUND)));
        return new Contribution("spring-amqp", points);
    }

    private boolean collectInbound(AnnotatedElement element, Set<String> exchanges, Set<String> queues) {
        var listener = AnnotatedElementUtils.findMergedAnnotation(element, RabbitListener.class);
        if (listener == null) {
            return false;
        }
        for (QueueBinding binding : listener.bindings()) {
            var exchange = text(binding.exchange().value(), binding.exchange().name());
            if (exchange != null) {
                exchanges.add(exchange);
            }
        }
        for (String queue : listener.queues()) {
            if (StringUtils.hasText(queue)) {
                queues.add(queue);
            }
        }
        for (Queue queue : listener.queuesToDeclare()) {
            var name = text(queue.value(), queue.name());
            if (name != null) {
                queues.add(name);
            }
        }
        return true;
    }

    private void collectSendTo(AnnotatedElement element, Set<String> exchanges) {
        var sendTo = AnnotatedElementUtils.findMergedAnnotation(element, SendTo.class);
        if (sendTo == null) {
            return;
        }
        for (String destination : sendTo.value()) {
            // AMQP @SendTo destinations are "exchange/routingKey"; keep the exchange part.
            var exchange = destination == null ? "" : destination.split("/", 2)[0];
            if (StringUtils.hasText(exchange)) {
                exchanges.add(exchange);
            }
        }
    }

    private ContactPointInfo point(String key, String rawName, ContactDirection direction) {
        var resolved = resolve(rawName);
        var confidence = Placeholders.isUnresolved(resolved) ? MEDIUM : HIGH;
        var attribute = key.startsWith("amqp:exchange/") ? "exchange" : "queue";
        return new ContactPointInfo(key, AMQP, direction, confidence, null, Map.of(attribute, resolved));
    }

    private String resolve(String value) {
        return Placeholders.resolve(this.environment, value);
    }

    private static String text(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        return StringUtils.hasText(fallback) ? fallback : null;
    }

    private static boolean isUnresolved(String value) {
        return value.contains("${") || value.contains("#{") || value.contains("!{");
    }

}
