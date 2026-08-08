package com.weinhold.hexagon;

import static com.weinhold.hexagon.model.Direction.PRIMARY;
import static com.weinhold.hexagon.model.Direction.SECONDARY;
import static java.util.stream.Collectors.toSet;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jmolecules.architecture.hexagonal.Adapter;
import org.jmolecules.architecture.hexagonal.Port;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.event.annotation.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.NativeDetector;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import com.weinhold.hexagon.aot.HexagonComponentIndex;
import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.ComponentInfo;
import com.weinhold.hexagon.model.CoreInfo;
import com.weinhold.hexagon.model.Direction;
import com.weinhold.hexagon.model.EventsInfo;
import com.weinhold.hexagon.model.PortInfo;
import com.weinhold.hexagon.model.Provenance;

/**
 * Turns the types in the base packages into the endpoint's structural model: hexagonal
 * {@link PortInfo}/{@link AdapterInfo} entries and the DDD {@link CoreInfo} (aggregates and
 * domain events).
 *
 * <h2>Two passes, in this order</h2>
 * <ol>
 * <li><b>Annotations.</b> Types carrying jMolecules {@code @Port}/{@code @Adapter} (directly
 * or as a meta-annotation), {@code @AggregateRoot} or {@code @DomainEvent}. Direction is read
 * from the specific {@code @Primary*}/{@code @Secondary*} variant; a bare {@code @Port}
 * cannot express direction, so it defaults to {@link Direction#SECONDARY} with a warning.
 * Provenance is {@link Provenance#ANNOTATION}.</li>
 * <li><b>Conventions.</b> Whatever the first pass did not claim is offered to
 * {@link HexagonConventions}, so a service with no annotations at all still describes itself
 * (contract principle 3). Provenance is {@link Provenance#CONVENTION}, and annotations always
 * win, because a declared fact should never be overwritten by a guess.</li>
 * </ol>
 * Consumed events are then found by inspecting listener methods, with provenance
 * {@link Provenance#RUNTIME}.
 * <p>Candidate types are enumerated from class metadata, so only the types that turn out to
 * be part of the hexagon are actually loaded. Interfaces are included (unlike Spring's
 * default component scanning), because ports are almost always interfaces.
 */
public class HexagonScanner {

    private static final Logger log = LoggerFactory.getLogger(HexagonScanner.class);

    /**
     * Method-level annotations that mean "this type receives something". Referenced by name
     * because only {@code @EventListener} is guaranteed to be on the classpath.
     */
    private static final List<String> LISTENER_ANNOTATIONS = List.of("org.springframework.context.event.EventListener",
        "org.springframework.transaction.event.TransactionalEventListener",
        "org.springframework.kafka.annotation.KafkaListener", "org.springframework.amqp.rabbit.annotation.RabbitListener");

    private final Collection<String> basePackages;

    private final ClassLoader classLoader;

    private final HexagonConventions conventions;

    /** Scans for annotations only. */
    public HexagonScanner(Collection<String> basePackages, ClassLoader classLoader) {
        this(basePackages, classLoader, HexagonConventions.disabled());
    }

    public HexagonScanner(Collection<String> basePackages, ClassLoader classLoader, HexagonConventions conventions) {
        this.basePackages = basePackages;
        this.classLoader = classLoader;
        this.conventions = conventions;
    }

    /**
     * Result of a scan: the discovered ports and adapters (each sorted by id), the loaded
     * adapter classes keyed by id (so contact-point detectors can inspect them), and the
     * domain core, or {@code null} core when no domain components were found.
     */
    public record ScanResult(List<PortInfo> ports, List<AdapterInfo> adapters, Map<String, Class<?>> adapterTypes,
            CoreInfo core) {
    }

    public ScanResult scan() {
        return scan(candidates());
    }

    /**
     * The class names worth recording in the build-time index: everything either pass would
     * classify, plus every type carrying listener methods. Used by the AOT processor, which
     * runs while classpath scanning still works.
     */
    public List<String> relevantClassNames() {
        var relevant = new ArrayList<String>();
        candidates().forEach((className, metadata) -> {
            if (annotationStereotype(metadata) != null || this.conventions.classify(className, metadata) != null
                    || hasListenerMethods(metadata)) {
                relevant.add(className);
            }
        });
        relevant.sort(Comparator.naturalOrder());
        return relevant;
    }

    private ScanResult scan(Map<String, AnnotationMetadata> candidates) {
        var ports = new ArrayList<PortInfo>();
        var adapterCandidates = new ArrayList<AdapterCandidate>();
        var aggregates = new ArrayList<ComponentInfo>();
        var published = new ArrayList<ComponentInfo>();
        var claimed = new HashSet<String>();

        annotationPass(candidates, ports, adapterCandidates, aggregates, published, claimed);
        conventionPass(candidates, ports, adapterCandidates, published, claimed);

        var portIds = ports.stream().map(PortInfo::id).collect(toSet());
        var adapters = new ArrayList<AdapterInfo>();
        var adapterTypes = new LinkedHashMap<String, Class<?>>();
        for (var candidate : adapterCandidates) {
            // technology and contactPoints are filled in later by the contact-point detectors.
            adapters.add(new AdapterInfo(candidate.type().getName(), candidate.name(), candidate.direction(), null,
                candidate.provenance(), implementedPorts(candidate.type(), portIds), List.of()));
            adapterTypes.put(candidate.type().getName(), candidate.type());
        }

        var publishedIds = published.stream().map(ComponentInfo::id).collect(toSet());
        var consumed = consumedEvents(candidates, publishedIds);

        ports.sort(Comparator.comparing(PortInfo::id));
        adapters.sort(Comparator.comparing(AdapterInfo::id));
        return new ScanResult(ports, adapters, adapterTypes, buildCore(aggregates, published, consumed));
    }

    private void annotationPass(Map<String, AnnotationMetadata> candidates, List<PortInfo> ports,
        List<AdapterCandidate> adapters, List<ComponentInfo> aggregates, List<ComponentInfo> published,
        Set<String> claimed) {
        for (var entry : candidates.entrySet()) {
            var className = entry.getKey();
            var metadata = entry.getValue();
            var stereotype = annotationStereotype(metadata);
            if (stereotype == null) {
                continue;
            }
            switch (stereotype) {
                case PORT -> {
                    var type = load(className);
                    if (type != null) {
                        ports.add(toPort(type));
                        claimed.add(className);
                    }
                }
                case ADAPTER -> {
                    var type = load(className);
                    if (type != null) {
                        adapters.add(toAdapter(type));
                        claimed.add(className);
                    }
                }
                // Domain components contribute nothing but a name, so they are never loaded.
                case AGGREGATE -> {
                    aggregates.add(component(className, metadata, AggregateRoot.class, Provenance.ANNOTATION));
                    claimed.add(className);
                }
                case EVENT -> {
                    published.add(component(className, metadata, DomainEvent.class, Provenance.ANNOTATION));
                    claimed.add(className);
                }
            }
        }
    }

    private void conventionPass(Map<String, AnnotationMetadata> candidates, List<PortInfo> ports,
        List<AdapterCandidate> adapters, List<ComponentInfo> published, Set<String> claimed) {
        if (!this.conventions.isEnabled()) {
            return;
        }
        for (var entry : candidates.entrySet()) {
            var className = entry.getKey();
            if (claimed.contains(className)) {
                continue;
            }
            var classification = this.conventions.classify(className, entry.getValue());
            if (classification == null) {
                continue;
            }
            switch (classification.stereotype()) {
                case PORT -> {
                    var type = load(className);
                    if (type != null) {
                        ports.add(new PortInfo(className, type.getSimpleName(), classification.direction(),
                            Provenance.CONVENTION, operations(type)));
                        claimed.add(className);
                    }
                }
                case ADAPTER -> {
                    var type = load(className);
                    if (type != null) {
                        adapters.add(new AdapterCandidate(type, type.getSimpleName(), classification.direction(),
                            Provenance.CONVENTION));
                        claimed.add(className);
                    }
                }
                case EVENT -> {
                    published.add(new ComponentInfo(className, HexagonConventions.simpleName(className),
                        Provenance.CONVENTION));
                    claimed.add(className);
                }
            }
        }
    }

    /**
     * Events this service receives. They are typically defined elsewhere — that is the whole
     * point, it is the other side of somebody's published event — so they cannot be found by
     * scanning our own packages for {@code @DomainEvent}. Instead the listener methods in the
     * base packages are inspected and their payload types taken, which is runtime inspection
     * and is reported as such.
     */
    private List<ComponentInfo> consumedEvents(Map<String, AnnotationMetadata> candidates, Set<String> publishedIds) {
        var consumed = new LinkedHashMap<String, ComponentInfo>();
        for (var entry : candidates.entrySet()) {
            if (!hasListenerMethods(entry.getValue())) {
                continue;
            }
            var type = load(entry.getKey());
            if (type == null) {
                continue;
            }
            for (var method : type.getDeclaredMethods()) {
                if (method.isSynthetic()) {
                    continue;
                }
                var annotations = MergedAnnotations.from(method);
                var listener = LISTENER_ANNOTATIONS.stream().filter(annotations::isPresent).findFirst().orElse(null);
                if (listener == null) {
                    continue;
                }
                for (var eventType : payloadTypes(method, annotations, listener)) {
                    var id = eventType.getName();
                    if (publishedIds.contains(id) || consumed.containsKey(id) || !isDomainEvent(eventType)) {
                        continue;
                    }
                    consumed.put(id, new ComponentInfo(id, eventName(eventType), Provenance.RUNTIME));
                }
            }
        }
        var result = new ArrayList<>(consumed.values());
        result.sort(Comparator.comparing(ComponentInfo::id));
        return result;
    }

    /**
     * The types a listener method receives: an explicit {@code classes} attribute when the
     * annotation has one (an {@code @EventListener} may declare its types and take no
     * parameter at all), otherwise the parameter types.
     */
    private static List<Class<?>> payloadTypes(Method method, MergedAnnotations annotations, String listener) {
        var declared = annotations.get(listener).getValue("classes", Class[].class).orElse(new Class<?>[0]);
        return declared.length > 0 ? List.of(declared) : List.of(method.getParameterTypes());
    }

    private boolean isDomainEvent(Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.getName().startsWith("java.")) {
            return false;
        }
        if (MergedAnnotations.from(type).isPresent(DomainEvent.class)) {
            return true;
        }
        // Without the annotation the only evidence is the name, which is a convention.
        return this.conventions.isEnabled() && this.conventions.looksLikeEvent(type.getName());
    }

    private static String eventName(Class<?> type) {
        var annotation = MergedAnnotations.from(type).get(DomainEvent.class);
        if (annotation.isPresent()) {
            var name = annotation.getValue("name", String.class).orElse(null);
            if (StringUtils.hasText(name)) {
                return name;
            }
        }
        return type.getSimpleName();
    }

    private static boolean hasListenerMethods(AnnotationMetadata metadata) {
        return LISTENER_ANNOTATIONS.stream().anyMatch(metadata::hasAnnotatedMethods);
    }

    private static CoreInfo buildCore(List<ComponentInfo> aggregates, List<ComponentInfo> publishedEvents,
        List<ComponentInfo> consumedEvents) {
        aggregates.sort(Comparator.comparing(ComponentInfo::id));
        publishedEvents.sort(Comparator.comparing(ComponentInfo::id));

        var events = (publishedEvents.isEmpty() && consumedEvents.isEmpty()) ? null
                : new EventsInfo(List.copyOf(publishedEvents), List.copyOf(consumedEvents));
        if (aggregates.isEmpty() && events == null) {
            return null;
        }

        // The base package is derived from what the service owns; a consumed event lives in
        // somebody else's package and would drag the shared prefix back up to "com".
        var componentIds = new ArrayList<String>();
        aggregates.forEach(component -> componentIds.add(component.id()));
        publishedEvents.forEach(component -> componentIds.add(component.id()));
        return new CoreInfo(commonBasePackage(componentIds), aggregates, events);
    }

    // -------------------------------------------------------------------------------------
    // Candidate discovery
    // -------------------------------------------------------------------------------------

    /** Every type in the base packages, as metadata, without loading any of them. */
    private Map<String, AnnotationMetadata> candidates() {
        var index = HexagonComponentIndex.load(this.classLoader);
        if (index != null) {
            return fromIndex(index);
        }
        if (NativeDetector.inNativeImage()) {
            log.warn("Running in a native image without a hexagon component index ({}): classpath scanning cannot "
                    + "work here, so /actuator/hexagon will be empty. Build with Spring's AOT processing enabled.",
                HexagonComponentIndex.RESOURCE_LOCATION);
            return Map.of();
        }
        return fromClasspathScan();
    }

    private Map<String, AnnotationMetadata> fromIndex(HexagonComponentIndex index) {
        var found = new LinkedHashMap<String, AnnotationMetadata>();
        for (var className : index.getClassNames()) {
            if (!isUnderBasePackages(className)) {
                continue;
            }
            var type = load(className);
            if (type != null) {
                found.put(className, AnnotationMetadata.introspect(type));
            }
        }
        return found;
    }

    private Map<String, AnnotationMetadata> fromClasspathScan() {
        var provider = new ClassPathScanningCandidateComponentProvider(false) {

            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                // Ports are interfaces; the default implementation would reject them.
                return true;
            }
        };
        // Everything is a candidate: the two passes decide, not the scan. Reading metadata is
        // what costs here, and the filter would not have saved that.
        provider.addIncludeFilter((TypeFilter) (metadataReader, metadataReaderFactory) -> true);

        var found = new LinkedHashMap<String, AnnotationMetadata>();
        for (String basePackage : this.basePackages) {
            if (!StringUtils.hasText(basePackage)) {
                continue;
            }
            for (BeanDefinition definition : provider.findCandidateComponents(basePackage)) {
                var className = definition.getBeanClassName();
                if (className == null || found.containsKey(className)
                        || !(definition instanceof AnnotatedBeanDefinition annotated)) {
                    continue;
                }
                found.put(className, annotated.getMetadata());
            }
        }
        return found;
    }

    private boolean isUnderBasePackages(String className) {
        if (this.basePackages.isEmpty()) {
            return false;
        }
        return this.basePackages.stream().filter(StringUtils::hasText)
                                .anyMatch(basePackage -> className.equals(basePackage)
                                        || className.startsWith(basePackage + "."));
    }

    private Class<?> load(String className) {
        try {
            return ClassUtils.forName(className, this.classLoader);
        } catch (Throwable ex) {
            log.warn("Skipping hexagon candidate '{}': {}", className, ex.toString());
            return null;
        }
    }

    // -------------------------------------------------------------------------------------
    // Annotation pass
    // -------------------------------------------------------------------------------------

    private enum Stereotype {
        PORT, ADAPTER, AGGREGATE, EVENT
    }

    private record AdapterCandidate(Class<?> type, String name, Direction direction, Provenance provenance) {
    }

    private static Stereotype annotationStereotype(AnnotationMetadata metadata) {
        var annotations = metadata.getAnnotations();
        if (annotations.isPresent(Port.class)) {
            return Stereotype.PORT;
        }
        if (annotations.isPresent(Adapter.class)) {
            return Stereotype.ADAPTER;
        }
        if (annotations.isPresent(AggregateRoot.class)) {
            return Stereotype.AGGREGATE;
        }
        if (annotations.isPresent(DomainEvent.class)) {
            return Stereotype.EVENT;
        }
        return null;
    }

    private PortInfo toPort(Class<?> type) {
        // DIRECT strategy: only annotations on the type itself (plus their meta-annotations),
        // never inherited from implemented port interfaces or superclasses.
        var annotations = MergedAnnotations.from(type);
        var direction = direction(type, annotations, PrimaryPort.class, SecondaryPort.class, "@Port");
        var name = name(type, annotations, PrimaryPort.class, SecondaryPort.class, Port.class);
        return new PortInfo(type.getName(), name, direction, Provenance.ANNOTATION, operations(type));
    }

    private AdapterCandidate toAdapter(Class<?> type) {
        var annotations = MergedAnnotations.from(type);
        var direction = direction(type, annotations, PrimaryAdapter.class, SecondaryAdapter.class, "@Adapter");
        var name = name(type, annotations, PrimaryAdapter.class, SecondaryAdapter.class, Adapter.class);
        return new AdapterCandidate(type, name, direction, Provenance.ANNOTATION);
    }

    private static ComponentInfo component(String className, AnnotationMetadata metadata,
        Class<? extends Annotation> variant, Provenance provenance) {
        var annotation = metadata.getAnnotations().get(variant);
        var name = annotation.isPresent() ? annotation.getValue("name", String.class).orElse(null) : null;
        return new ComponentInfo(className,
            StringUtils.hasText(name) ? name : HexagonConventions.simpleName(className), provenance);
    }

    private static Direction direction(Class<?> type, MergedAnnotations annotations, Class<? extends Annotation> primary,
        Class<? extends Annotation> secondary, String stereotype) {
        if (annotations.isPresent(primary)) {
            return PRIMARY;
        }
        if (annotations.isPresent(secondary)) {
            return SECONDARY;
        }
        log.warn("'{}' is annotated with {} but neither its primary nor secondary variant: defaulting direction to SECONDARY",
            type.getName(), stereotype);
        return SECONDARY;
    }

    @SafeVarargs
    private static String name(Class<?> type, MergedAnnotations annotations, Class<? extends Annotation>... variants) {
        for (var variant : variants) {
            var annotation = annotations.get(variant);
            if (annotation.isPresent()) {
                // getValue (not getString) so annotations without a 'name' attribute don't blow up.
                var name = annotation.getValue("name", String.class).orElse(null);
                if (StringUtils.hasText(name)) {
                    return name;
                }
            }
        }
        return type.getSimpleName();
    }

    /**
     * The port's operations. {@code getMethods()} rather than the declared ones, so a port
     * interface that extends another still lists the operations it inherited — those are part
     * of the hole in the hexagon just the same.
     */
    private static List<String> operations(Class<?> type) {
        return Arrays.stream(type.getMethods())
                     .filter(method -> !method.isSynthetic())
                     .filter(method -> method.getDeclaringClass() != Object.class)
                     .map(Method::getName)
                     .distinct()
                     .sorted()
                     .toList();
    }

    private static List<String> implementedPorts(Class<?> type, Set<String> portIds) {
        return ClassUtils.getAllInterfacesForClassAsSet(type)
                         .stream()
                         .map(Class::getName)
                         .filter(portIds::contains)
                         .sorted()
                         .toList();
    }

    /**
     * The longest package shared by all given class names, e.g. the domain base package
     * for {@code com.acme.orders.domain.Order} and {@code com.acme.orders.domain.event.X}
     * is {@code com.acme.orders.domain}.
     */
    private static String commonBasePackage(Collection<String> classNames) {
        var segmented = classNames.stream()
                                  .map(HexagonScanner::packageOf)
                                  .filter(StringUtils::hasText)
                                  .map(name -> name.split("\\."))
                                  .toList();
        if (segmented.isEmpty()) {
            return null;
        }
        var first = segmented.getFirst();
        var shared = first.length;
        for (var segments : segmented) {
            var i = 0;
            while (i < shared && i < segments.length && segments[i].equals(first[i])) {
                i++;
            }
            shared = i;
        }
        return shared == 0 ? null : String.join(".", Arrays.copyOfRange(first, 0, shared));
    }

    private static String packageOf(String className) {
        var lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? "" : className.substring(0, lastDot);
    }

}
