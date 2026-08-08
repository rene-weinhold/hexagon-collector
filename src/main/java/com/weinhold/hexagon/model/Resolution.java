package com.weinhold.hexagon.model;

/**
 * How an {@code OUTBOUND} contact point's target service was determined. A contact point
 * whose target could not be determined at all carries no {@link TargetInfo} — the collector
 * resolves it by path instead — so there is deliberately no {@code UNRESOLVED} constant.
 */
public enum Resolution {

        /** Read from the {@code hexagon.collection.targets} mapping. */
        CONFIG,

        /**
         * The adapter names a logical service that a discovery client resolves at runtime, e.g.
         * {@code @FeignClient(name = "inventory-service")}.
         */
        SERVICE_DISCOVERY,

        /** Declared on the adapter itself, e.g. an explicit URL on the client annotation. */
        ANNOTATION

}
