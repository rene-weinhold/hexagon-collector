package com.weinhold.hexagon;

import static com.weinhold.hexagon.model.HexagonDescriptor.SCHEMA_VERSION;
import static java.util.Comparator.comparing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import com.weinhold.hexagon.contact.ContactPointDetector;
import com.weinhold.hexagon.contact.ContactPointDetector.AdapterContext;
import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.HexagonDescriptor;
import com.weinhold.hexagon.model.ServiceInfo;

/**
 * Builds the {@link HexagonDescriptor} once and caches it. The build runs the
 * {@link HexagonScanner} and then enriches each adapter by running it through the registered
 * {@link ContactPointDetector}s. Computation is deferred to first access so that any Spring
 * beans the detectors inspect (handler mappings, listener registries, data sources) are
 * fully initialized — this is the contract's "computed once at startup, served afterward".
 */
public class HexagonDescriptorFactory {

    private static final Logger log = LoggerFactory.getLogger(HexagonDescriptorFactory.class);

    private final BeanFactory beanFactory;

    private final Environment environment;

    private final HexagonCollectionProperties properties;

    private final ObjectProvider<BuildProperties> buildProperties;

    private final List<ContactPointDetector> detectors;

    private final AtomicReference<HexagonDescriptor> cache = new AtomicReference<>();

    public HexagonDescriptorFactory(BeanFactory beanFactory, Environment environment, HexagonCollectionProperties properties,
        ObjectProvider<BuildProperties> buildProperties, List<ContactPointDetector> detectors) {
        this.beanFactory = beanFactory;
        this.environment = environment;
        this.properties = properties;
        this.buildProperties = buildProperties;
        this.detectors = detectors;
    }

    public HexagonDescriptor get() {
        var descriptor = this.cache.get();
        if (descriptor == null) {
            synchronized (this) {
                descriptor = this.cache.get();
                if (descriptor == null) {
                    descriptor = build();
                    this.cache.set(descriptor);
                }
            }
        }
        return descriptor;
    }

    private HexagonDescriptor build() {
        var basePackages = resolveBasePackages();
        var scan = new HexagonScanner(basePackages, resolveClassLoader(),
            new HexagonConventions(this.properties.getConventions())).scan();

        var adapters = scan.adapters().stream().map(adapter -> enrich(adapter, scan.adapterTypes().get(adapter.id()))).toList();

        var service = buildServiceInfo(basePackages);
        return new HexagonDescriptor(SCHEMA_VERSION, Instant.now(), service, scan.core(), scan.ports(), adapters);
    }

    private AdapterInfo enrich(AdapterInfo base, Class<?> type) {
        if (type == null || this.detectors.isEmpty()) {
            return base;
        }
        var context = new AdapterContext(type, base);
        var technology = base.technology();
        var byKey = new LinkedHashMap<PointKey, ContactPointInfo>();
        for (var detector : this.detectors) {
            var contribution = detector.detect(context);
            if (technology == null) {
                technology = contribution.technology();
            }
            for (var point : contribution.contactPoints()) {
                byKey.putIfAbsent(new PointKey(point.key(), point.direction()), point);
            }
        }

        var contactPoints = new ArrayList<>(byKey.values());
        contactPoints.sort(comparing(ContactPointInfo::key).thenComparing(ContactPointInfo::direction));
        return new AdapterInfo(base.id(), base.name(), base.direction(), technology, base.provenance(), base.implementsPorts(),
            contactPoints);
    }

    /**
     * Identity of a contact point for deduplication. The direction is part of it: an adapter
     * that both serves and calls the same route has two distinct touchpoints, and collapsing
     * them would delete one end of an edge.
     */
    private record PointKey(String key, ContactDirection direction) {
    }

    private List<String> resolveBasePackages() {
        if (!this.properties.getBasePackages().isEmpty()) {
            return this.properties.getBasePackages();
        }
        if (AutoConfigurationPackages.has(this.beanFactory)) {
            return AutoConfigurationPackages.get(this.beanFactory);
        }
        // Silently describing nothing is the worst possible outcome: the endpoint answers 200
        // with an empty hexagon and looks like the service simply has no structure.
        log.warn("No base packages to scan: neither 'hexagon.collection.base-packages' is set nor are there "
            + "auto-configuration packages. /actuator/hexagon will report no ports or adapters.");
        return List.of();
    }

    private ClassLoader resolveClassLoader() {
        if (this.beanFactory instanceof ConfigurableBeanFactory configurable && configurable.getBeanClassLoader() != null) {
            return configurable.getBeanClassLoader();
        }
        return ClassUtils.getDefaultClassLoader();
    }

    private ServiceInfo buildServiceInfo(List<String> basePackages) {
        var service = this.properties.getService();
        var build = this.buildProperties.getIfAvailable();
        var id = firstText(service.getId(), this.environment.getProperty("spring.application.name"), "application");
        var version = firstText(service.getVersion(), build != null ? build.getVersion() : null);
        var basePackage = basePackages.isEmpty() ? null : basePackages.getFirst();
        return new ServiceInfo(id, service.getDisplayName(), version, service.getEnvironment(), resolveInstanceId(), basePackage,
            service.getRepository());
    }

    /**
     * Distinguishes one running instance from another so a collector polling ten pods can
     * deduplicate them. Under Kubernetes {@code HOSTNAME} is the pod name, which is exactly
     * the identifier the contract's example carries.
     */
    private String resolveInstanceId() {
        return firstText(this.properties.getService().getInstanceId(),
            this.environment.getProperty("spring.application.instance-id"), this.environment.getProperty("HOSTNAME"));
    }

    private static String firstText(String... candidates) {
        for (var candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

}
