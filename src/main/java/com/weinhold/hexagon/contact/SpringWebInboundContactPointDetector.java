package com.weinhold.hexagon.contact;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Reports the HTTP routes a servlet-stack adapter serves as {@code INBOUND}
 * {@code http:{METHOD} {pathTemplate}} contact points, read from Spring MVC's
 * {@link RequestMappingHandlerMapping}.
 *
 * @see WebFluxInboundContactPointDetector for the reactive stack
 */
public class SpringWebInboundContactPointDetector extends HandlerMappingContactPointDetector<RequestMappingInfo> {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    public SpringWebInboundContactPointDetector(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        super("spring-web");
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
        var pathPatterns = info.getPathPatternsCondition();
        if (pathPatterns != null) {
            return pathPatterns.getPatternValues();
        }
        var patterns = info.getPatternsCondition();
        return patterns != null ? patterns.getPatterns() : Set.of();
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
