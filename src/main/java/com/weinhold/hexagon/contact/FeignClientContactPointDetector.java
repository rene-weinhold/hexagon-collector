package com.weinhold.hexagon.contact;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.weinhold.hexagon.model.Confidence;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.Protocol;
import com.weinhold.hexagon.model.Resolution;
import com.weinhold.hexagon.model.TargetInfo;

/**
 * Reports the calls a {@code @FeignClient} makes as {@code OUTBOUND} HTTP contact points.
 * <p>Feign is the one outbound client that names its target itself:
 * {@code @FeignClient("inventory-service")} is a logical service id, resolved through service
 * discovery — exactly the identifier the collector needs to draw a confirmed edge, and the
 * reason open decision 1 of the contract is much less painful here than for a bare
 * {@code RestClient}.
 * <p>The annotation is read <em>by name</em> rather than compiled against, so the starter
 * does not drag in a Spring Cloud release train — and stays compatible with whichever one the
 * consumer is on. The request mappings themselves are ordinary Spring MVC annotations.
 */
public class FeignClientContactPointDetector implements ContactPointDetector {

    static final String FEIGN_CLIENT = "org.springframework.cloud.openfeign.FeignClient";

    private final Environment environment;

    private final Map<String, String> targets;

    public FeignClientContactPointDetector(Environment environment, Map<String, String> targets) {
        this.environment = environment;
        this.targets = targets;
    }

    @Override
    public Contribution detect(AdapterContext adapter) {
        var type = adapter.type();
        var feignClient = MergedAnnotations.from(type).get(FEIGN_CLIENT);
        if (!feignClient.isPresent()) {
            return Contribution.none();
        }

        var target = resolveTarget(type, feignClient);
        var basePath = Placeholders.resolve(this.environment, feignClient.getValue("path", String.class).orElse(""));

        var byKey = new LinkedHashMap<String, ContactPointInfo>();
        for (var method : requestMethods(type)) {
            var mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            for (var point : toContactPoints(mapping, basePath, target)) {
                byKey.putIfAbsent(point.key(), point);
            }
        }

        if (byKey.isEmpty()) {
            return Contribution.none();
        }
        return new Contribution("spring-cloud-openfeign", new ArrayList<>(byKey.values()));
    }

    /**
     * Where the client points. A {@code hexagon.collection.targets} entry wins, because an
     * operator overriding the mapping knows something the code does not; otherwise the
     * annotation's own name is the target, resolved by discovery unless a fixed {@code url}
     * was given.
     */
    private TargetInfo resolveTarget(Class<?> type, MergedAnnotation<?> feignClient) {
        var configured = this.targets.get(type.getName());
        if (StringUtils.hasText(configured)) {
            return new TargetInfo(configured, Resolution.CONFIG);
        }
        var name = Placeholders.resolve(this.environment, feignClient.getValue("name", String.class).orElse(""));
        if (!StringUtils.hasText(name)) {
            return null;
        }
        var url = feignClient.getValue("url", String.class).orElse("");
        return new TargetInfo(name, StringUtils.hasText(url) ? Resolution.ANNOTATION : Resolution.SERVICE_DISCOVERY);
    }

    private List<ContactPointInfo> toContactPoints(RequestMapping mapping, String basePath, TargetInfo target) {
        var path = Placeholders.resolve(this.environment, join(basePath, firstPath(mapping)));
        var methods = mapping.method();

        var points = new ArrayList<ContactPointInfo>();
        if (methods.length == 0) {
            points.add(toContactPoint("*", path, target));
            return points;
        }
        for (RequestMethod method : methods) {
            points.add(toContactPoint(method.name(), path, target));
        }
        return points;
    }

    private static ContactPointInfo toContactPoint(String method, String path, TargetInfo target) {
        var attributes = new LinkedHashMap<String, Object>();
        attributes.put("method", CanonicalKey.normalizeMethod(method));
        attributes.put("pathTemplate", CanonicalKey.normalizePath(path));

        var confidence = Confidence.HIGH;
        if ("*".equals(method)) {
            confidence = confidence.downgrade();
        }
        if (Placeholders.isUnresolved(path)) {
            confidence = confidence.downgrade();
        }

        if (target == null) {
            return new ContactPointInfo(CanonicalKey.http(method, path), Protocol.HTTP, ContactDirection.OUTBOUND,
                confidence.downgrade(), null, attributes);
        }
        attributes.put("targetService", target.logicalService());
        return new ContactPointInfo(CanonicalKey.httpOutbound(target.logicalService(), method, path), Protocol.HTTP,
            ContactDirection.OUTBOUND, confidence, target, attributes);
    }

    private static String firstPath(RequestMapping mapping) {
        var paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
        return paths.length > 0 ? paths[0] : "";
    }

    private static Set<Method> requestMethods(Class<?> type) {
        Set<Method> methods = new LinkedHashSet<>(List.of(type.getMethods()));
        for (Class<?> implemented : ClassUtils.getAllInterfacesForClass(type)) {
            methods.addAll(List.of(implemented.getMethods()));
        }
        return methods;
    }

    private static String join(String base, String path) {
        if (!StringUtils.hasText(base)) {
            return path;
        }
        if (!StringUtils.hasText(path)) {
            return base;
        }
        return base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
    }

}
