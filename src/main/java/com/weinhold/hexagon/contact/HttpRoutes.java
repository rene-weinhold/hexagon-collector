package com.weinhold.hexagon.contact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.weinhold.hexagon.model.Confidence;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.Protocol;

/**
 * Turns resolved routes into {@code INBOUND} HTTP contact points. Shared by the servlet and
 * reactive detectors so that both sides of a landscape produce byte-identical keys — a
 * WebFlux service serving {@code GET /api/orders/{id}} has to match the servlet service
 * calling it, and any divergence here silently breaks edge matching.
 */
final class HttpRoutes {

    private HttpRoutes() {
    }

    /**
     * @param patterns path templates, already carrying {@code {var}} placeholders
     * @param methods HTTP method names; empty means the route answers any method
     */
    static List<ContactPointInfo> inbound(Collection<String> patterns, Collection<String> methods) {
        var points = new ArrayList<ContactPointInfo>();
        for (var pattern : patterns) {
            if (methods.isEmpty()) {
                // A route with no method condition answers all of them, so the key can only
                // be a wildcard — which will match more loosely in the collector.
                points.add(point("*", pattern, Confidence.MEDIUM));
            } else {
                for (var method : methods) {
                    points.add(point(method, pattern, Confidence.HIGH));
                }
            }
        }
        return points;
    }

    private static ContactPointInfo point(String method, String pattern, Confidence confidence) {
        return new ContactPointInfo(CanonicalKey.httpInbound(method, pattern), Protocol.HTTP, ContactDirection.INBOUND,
            confidence, null, Map.of("method", method, "pathTemplate", CanonicalKey.normalizePath(pattern)));
    }

}
