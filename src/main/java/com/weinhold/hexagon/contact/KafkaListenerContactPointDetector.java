package com.weinhold.hexagon.contact;

import static com.weinhold.hexagon.contact.CanonicalKey.kafkaTopic;
import static com.weinhold.hexagon.model.Confidence.HIGH;
import static com.weinhold.hexagon.model.Confidence.MEDIUM;
import static com.weinhold.hexagon.model.ContactDirection.INBOUND;
import static com.weinhold.hexagon.model.ContactDirection.OUTBOUND;
import static com.weinhold.hexagon.model.Protocol.KAFKA;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;

import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;

/**
 * Reports Kafka topics an adapter consumes ({@code @KafkaListener}) as {@code INBOUND} and
 * topics it forwards to ({@code @SendTo} on a listener) as {@code OUTBOUND}
 * {@code kafka:topic/{topic}} contact points. Property placeholders in topic names are
 * resolved against the environment; unresolved placeholders/SpEL drop confidence to
 * {@code MEDIUM}.
 * <p>Only the declarative sides are reported: imperative {@code KafkaTemplate.send(...)}
 * carries no static metadata. {@code @SendTo} is only considered when the adapter also has a
 * {@code @KafkaListener}, so it is not mistaken for another transport.
 */
public class KafkaListenerContactPointDetector implements ContactPointDetector {

    private final Environment environment;

    public KafkaListenerContactPointDetector(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Contribution detect(AdapterContext adapter) {
        var inbound = new LinkedHashSet<String>();
        var hasListener = collect(adapter.type(), KafkaListener.class, KafkaListener::topics, inbound);
        for (var method : adapter.type().getDeclaredMethods()) {
            hasListener |= collect(method, KafkaListener.class, KafkaListener::topics, inbound);
        }
        if (!hasListener) {
            return Contribution.none();
        }

        var outbound = new LinkedHashSet<String>();
        collect(adapter.type(), SendTo.class, SendTo::value, outbound);
        for (var method : adapter.type().getDeclaredMethods()) {
            collect(method, SendTo.class, SendTo::value, outbound);
        }

        var points = new ArrayList<ContactPointInfo>();
        inbound.forEach(topic -> points.add(topicContactPoint(topic, INBOUND)));
        outbound.forEach(topic -> points.add(topicContactPoint(topic, OUTBOUND)));
        return new Contribution("spring-kafka", points);
    }

    private ContactPointInfo topicContactPoint(String rawTopic, ContactDirection direction) {
        var topic = Placeholders.resolve(this.environment, rawTopic);
        var confidence = Placeholders.isUnresolved(topic) ? MEDIUM : HIGH;
        return new ContactPointInfo(kafkaTopic(topic), KAFKA, direction, confidence, null, Map.of("topic", topic));
    }

    private static <A extends Annotation> boolean collect(AnnotatedElement element, Class<A> annotationType,
        Function<A, String[]> values, Set<String> into) {
        var annotation = AnnotatedElementUtils.findMergedAnnotation(element, annotationType);
        if (annotation == null) {
            return false;
        }
        for (var value : values.apply(annotation)) {
            if (value != null && !value.isBlank()) {
                into.add(value);
            }
        }
        return true;
    }

}
