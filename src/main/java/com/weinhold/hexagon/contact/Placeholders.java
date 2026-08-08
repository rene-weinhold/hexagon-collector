package com.weinhold.hexagon.contact;

import org.springframework.core.env.Environment;

/**
 * Property-placeholder handling shared by the detectors. Topic names, exchange names and
 * request paths are routinely externalized (<code>${orders.topic}</code>); the canonical key
 * has to carry the resolved value, and when it cannot be resolved the contact point must say
 * so through a lower {@code confidence} rather than publishing a key no other service will
 * ever match (contract principle 4).
 */
public final class Placeholders {

    private Placeholders() {
    }

    /** Resolves {@code ${...}} placeholders against the environment, leaving unknown ones in place. */
    public static String resolve(Environment environment, String value) {
        return value == null ? null : environment.resolvePlaceholders(value);
    }

    /**
     * Whether the value still contains a placeholder or SpEL expression after resolution —
     * i.e. whether the canonical key built from it is a guess rather than a fact.
     */
    public static boolean isUnresolved(String value) {
        return value != null && (value.contains("${") || value.contains("#{") || value.contains("!{"));
    }

}
