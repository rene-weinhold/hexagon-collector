package com.weinhold.hexagon.contact;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

/**
 * The reactive counterpart of {@link SpringWebInboundContactPointDetector}: inbound routes
 * read from WebFlux's {@link RequestMappingHandlerMapping}.
 * <p>Without this a WebFlux service would report adapters with no contact points at all and
 * simply vanish from the inbound half of the landscape — the kind of silent gap that makes a
 * map untrustworthy.
 */
public class WebFluxInboundContactPointDetector extends HandlerMappingContactPointDetector<RequestMappingInfo> {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    public WebFluxInboundContactPointDetector(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        super("spring-webflux");
        this.handlerMappings = handlerMappings;
    }

    @Override
    protected Map<RequestMappingInfo, HandlerMethod> handlerMethods() {
        var all = new HashMap<RequestMappingInfo, HandlerMethod>();
        this.handlerMappings.forEach(mapping -> all.putAll(mapping.getHandlerMethods()));
        return all;
    }

    @Override
    protected Set<String> patterns(RequestMappingInfo info) {
        var condition = info.getPatternsCondition();
        if (condition == null) {
            return Set.of();
        }
        return condition.getPatterns()
                        .stream()
                        .map(PathPattern::getPatternString)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    protected Set<String> methods(RequestMappingInfo info) {
        return info.getMethodsCondition()
                   .getMethods()
                   .stream()
                   .map(RequestMethod::name)
                   .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
