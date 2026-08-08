package com.weinhold.hexagon.contact;

import java.util.List;

import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.ContactPointInfo;

/**
 * Strategy for discovering the outward touchpoints of a single adapter from a specific
 * technology (Spring MVC, Kafka, JDBC, ...). Implementations are registered as Spring beans
 * guarded by {@code @ConditionalOnClass}, so only the technologies actually on the
 * classpath contribute.
 * <p>Detectors are invoked lazily, the first time the endpoint is read, so any framework
 * beans they inspect (handler mappings, listener registries, data sources) are fully
 * initialized by then.
 */
public interface ContactPointDetector {

    /**
     * Inspect a single adapter and return the contact points this technology contributes.
     *
     * @param adapter the adapter under inspection
     * @return the contribution, or {@link Contribution#none()} if this detector does not
     *         apply to the adapter
     */
    Contribution detect(AdapterContext adapter);

    /**
     * The adapter being inspected: its loaded {@link Class} and the base {@link AdapterInfo}
     * already produced by the annotation scan.
     */
    record AdapterContext(Class<?> type, AdapterInfo base) {
    }

    /**
     * What a detector contributes for one adapter: an optional {@code technology} label
     * (used for the UI icon) and any discovered contact points.
     */
    record Contribution(String technology, List<ContactPointInfo> contactPoints) {

        private static final Contribution NONE = new Contribution(null, List.of());

        public static Contribution none() {
            return NONE;
        }

        public boolean isEmpty() {
            return this.technology == null && this.contactPoints.isEmpty();
        }
    }

}
