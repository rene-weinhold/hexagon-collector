package com.weinhold.hexagon.contact;

import java.util.Locale;

/**
 * Builds the canonical matching keys defined by the contract. The entire edge-matching in
 * the collector hinges on two services independently producing the identical string, so the
 * normalization rules live in exactly one place here: method upper-cased, no query string,
 * no trailing slash, path variables kept as {@code {name}}.
 */
public final class CanonicalKey {

    private CanonicalKey() {
    }

    /**
     * {@code http:{METHOD} {pathTemplate}}, e.g. {@code http:POST /api/orders}. Used for
     * inbound routes and for outbound calls whose target service is unknown — the collector
     * matches the two by path.
     */
    public static String http(String method, String pathTemplate) {
        return "http:" + normalizeMethod(method) + " " + normalizePath(pathTemplate);
    }

    /** {@code http:{METHOD} {pathTemplate}} — alias of {@link #http} for inbound routes. */
    public static String httpInbound(String method, String pathTemplate) {
        return http(method, pathTemplate);
    }

    /** {@code http:{service}:{METHOD} {pathTemplate}} for outbound calls to a known service. */
    public static String httpOutbound(String logicalService, String method, String pathTemplate) {
        return "http:" + logicalService + ":" + normalizeMethod(method) + " " + normalizePath(pathTemplate);
    }

    /** {@code kafka:topic/{topic}}. */
    public static String kafkaTopic(String topic) {
        return "kafka:topic/" + topic;
    }

    /** {@code amqp:exchange/{exchange}}. */
    public static String amqpExchange(String exchange) {
        return "amqp:exchange/" + exchange;
    }

    /**
     * {@code amqp:queue/{queue}} — an extension to the schema for AMQP listeners bound to a
     * queue with no declared exchange (e.g. the default exchange).
     */
    public static String amqpQueue(String queue) {
        return "amqp:queue/" + queue;
    }

    /** {@code jdbc:{vendor}/{database}}, or {@code jdbc:{vendor}} when the database is unknown. */
    public static String jdbc(String vendor, String database) {
        return (database == null || database.isBlank()) ? "jdbc:" + vendor : "jdbc:" + vendor + "/" + database;
    }

    static String normalizeMethod(String method) {
        return (method == null || method.isBlank()) ? "*" : method.trim().toUpperCase(Locale.ROOT);
    }

    static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        var normalized = path.trim();
        var query = normalized.indexOf('?');
        if (query >= 0) {
            normalized = normalized.substring(0, query);
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

}
