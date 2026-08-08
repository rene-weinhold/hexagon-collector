package com.weinhold.hexagon.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A point where an adapter touches the outside world. This is the heart of the contract:
 * the collector later matches contact points across services (by {@link #key()}) to derive
 * edges. {@code key}, {@code protocol}, {@code direction} and {@code confidence} are required.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ContactPointInfo(String key, Protocol protocol, ContactDirection direction, Confidence confidence,
        TargetInfo target, Map<String, Object> attributes) {
}
