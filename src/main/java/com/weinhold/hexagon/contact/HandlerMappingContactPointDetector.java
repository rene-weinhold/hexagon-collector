package com.weinhold.hexagon.contact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.web.method.HandlerMethod;

import com.weinhold.hexagon.model.ContactPointInfo;

/**
 * Base for the detectors that read inbound HTTP routes out of a Spring handler mapping.
 * Rather than reparse annotations, they take the fully-resolved routes the framework itself
 * assembled, so path templates already carry {@code {var}} placeholders and HTTP methods are
 * exact — including routes contributed by a base class or a meta-annotation.
 * <p>The route index is built once, on first use — by then the handler mappings are
 * initialized — and keyed by the handler's class name so it can be matched to adapters.
 *
 * @param <M> the framework's request-mapping type, which differs between servlet and reactive
 */
abstract class HandlerMappingContactPointDetector<M> implements ContactPointDetector {

    private final String technology;

    private volatile Map<String, List<ContactPointInfo>> index;

    protected HandlerMappingContactPointDetector(String technology) {
        this.technology = technology;
    }

    @Override
    public Contribution detect(AdapterContext adapter) {
        var points = index().get(adapter.type().getName());
        if (points == null || points.isEmpty()) {
            return Contribution.none();
        }
        return new Contribution(this.technology, points);
    }

    /** Every route the application resolved, across all handler mappings of this flavour. */
    protected abstract Map<M, HandlerMethod> handlerMethods();

    protected abstract Set<String> patterns(M info);

    protected abstract Set<String> methods(M info);

    private Map<String, List<ContactPointInfo>> index() {
        if (this.index == null) {
            synchronized (this) {
                if (this.index == null) {
                    this.index = buildIndex();
                }
            }
        }
        return this.index;
    }

    private Map<String, List<ContactPointInfo>> buildIndex() {
        var byHandler = new HashMap<String, List<ContactPointInfo>>();
        handlerMethods().forEach((info, handler) -> {
            var handlerType = handler.getBeanType().getName();
            byHandler.computeIfAbsent(handlerType, key -> new ArrayList<>())
                     .addAll(HttpRoutes.inbound(patterns(info), methods(info)));
        });
        return byHandler;
    }

}
