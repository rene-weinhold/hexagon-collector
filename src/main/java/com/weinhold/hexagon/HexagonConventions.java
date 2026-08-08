package com.weinhold.hexagon;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.type.AnnotationMetadata;

import com.weinhold.hexagon.model.Direction;

/**
 * Classifies a type as a port, an adapter or a domain event from its package and class name
 * alone, for services that carry no jMolecules annotations.
 * <p>The contract requires the starter to work in a service that follows nothing but package
 * conventions (design principle 3), and equally requires it never to present a guess as a
 * fact (principle 4). Both are satisfied by classifying freely here and stamping everything
 * this class produces with {@code provenance: CONVENTION}, so the UI can draw it dashed.
 * Annotations always win: the scanner only consults conventions for types no annotation
 * claimed.
 * <h2>Rules</h2>
 * <ol>
 * <li>A package segment {@code port}/{@code ports} makes an <em>interface</em> a port; a
 * segment {@code adapter}/{@code adapters} makes a <em>concrete class</em> an adapter.</li>
 * <li>Direction comes from a neighbouring segment — {@code in}/{@code inbound}/{@code primary}/
 * {@code driving} versus {@code out}/{@code outbound}/{@code secondary}/{@code driven}.</li>
 * <li>Failing that, the class-name suffix decides, using the configurable lists under
 * {@code hexagon.collection.conventions}.</li>
 * <li>A concrete type whose name ends in {@code Event}, or that sits in an {@code event}
 * package, is a domain event.</li>
 * </ol>
 * Aggregates are deliberately <em>not</em> guessed: every type in a {@code domain} package
 * would qualify, and a core listing value objects and enums as aggregates is worse than an
 * empty one.
 */
public class HexagonConventions {

    private static final Set<String> PORT_SEGMENTS = Set.of("port", "ports");

    private static final Set<String> ADAPTER_SEGMENTS = Set.of("adapter", "adapters");

    private static final Set<String> PRIMARY_SEGMENTS = Set.of("in", "inbound", "primary", "driving");

    private static final Set<String> SECONDARY_SEGMENTS = Set.of("out", "outbound", "secondary", "driven");

    private static final Set<String> EVENT_SEGMENTS = Set.of("event", "events");

    private final boolean enabled;

    private final List<String> primaryPortSuffixes;

    private final List<String> secondaryPortSuffixes;

    private final List<String> primaryAdapterSuffixes;

    private final List<String> secondaryAdapterSuffixes;

    private final List<String> eventSuffixes;

    public HexagonConventions(HexagonCollectionProperties.Conventions properties) {
        this.enabled = properties.isEnabled();
        this.primaryPortSuffixes = List.copyOf(properties.getPrimaryPortSuffixes());
        this.secondaryPortSuffixes = List.copyOf(properties.getSecondaryPortSuffixes());
        this.primaryAdapterSuffixes = List.copyOf(properties.getPrimaryAdapterSuffixes());
        this.secondaryAdapterSuffixes = List.copyOf(properties.getSecondaryAdapterSuffixes());
        this.eventSuffixes = List.copyOf(properties.getEventSuffixes());
    }

    /** Conventions that classify nothing — annotation-only scanning. */
    public static HexagonConventions disabled() {
        var properties = new HexagonCollectionProperties.Conventions();
        properties.setEnabled(false);
        return new HexagonConventions(properties);
    }

    /** Default conventions, as if nothing had been configured. */
    public static HexagonConventions defaults() {
        return new HexagonConventions(new HexagonCollectionProperties.Conventions());
    }

    /** What a type looks like structurally. */
    public enum Stereotype {
            PORT, ADAPTER, EVENT
    }

    /** The verdict for one type; {@code direction} is null for events. */
    public record Classification(Stereotype stereotype, Direction direction) {
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /** The suffixes that mark a type as a domain event, shared with consumed-event detection. */
    public List<String> getEventSuffixes() {
        return this.eventSuffixes;
    }

    /** Whether the type looks like a domain event by name alone, ignoring its package. */
    public boolean looksLikeEvent(String className) {
        return endsWithAny(simpleName(className), this.eventSuffixes);
    }

    /**
     * Classifies a candidate, or returns {@code null} when nothing in its name or package
     * says what it is — which is the common case and must stay cheap and silent.
     */
    public Classification classify(String className, AnnotationMetadata metadata) {
        if (!this.enabled || metadata.isAnnotation() || !metadata.isIndependent()) {
            return null;
        }
        var simpleName = simpleName(className);
        if (simpleName.isEmpty() || simpleName.equals("package-info") || simpleName.equals("module-info")) {
            return null;
        }
        var segments = packageSegments(className);

        // 1. Package layout is the stronger signal: it is a deliberate architectural choice,
        // where a class-name suffix is often just house style.
        var direction = directionFrom(segments);
        if (metadata.isInterface() && containsAny(segments, PORT_SEGMENTS)) {
            return new Classification(Stereotype.PORT, direction != null ? direction : portDirectionFromName(simpleName));
        }
        if (metadata.isConcrete() && containsAny(segments, ADAPTER_SEGMENTS)) {
            return new Classification(Stereotype.ADAPTER, direction != null ? direction : adapterDirectionFromName(simpleName));
        }

        // 2. Otherwise fall back to the class name.
        if (metadata.isInterface()) {
            if (endsWithAny(simpleName, this.primaryPortSuffixes)) {
                return new Classification(Stereotype.PORT, Direction.PRIMARY);
            }
            if (endsWithAny(simpleName, this.secondaryPortSuffixes)) {
                return new Classification(Stereotype.PORT, Direction.SECONDARY);
            }
            return null;
        }
        if (!metadata.isConcrete()) {
            return null;
        }
        if (endsWithAny(simpleName, this.primaryAdapterSuffixes)) {
            return new Classification(Stereotype.ADAPTER, Direction.PRIMARY);
        }
        if (endsWithAny(simpleName, this.secondaryAdapterSuffixes)) {
            return new Classification(Stereotype.ADAPTER, Direction.SECONDARY);
        }
        // Checked last so that an OrderEventPublisher is read as a publisher, not an event.
        if (endsWithAny(simpleName, this.eventSuffixes) || containsAny(segments, EVENT_SEGMENTS)) {
            return new Classification(Stereotype.EVENT, null);
        }
        return null;
    }

    /**
     * A port package that does not say which side it is on is far more often a driven port
     * (repositories, gateways, clients) than a driving one, which matches the fallback the
     * annotation scan uses for a bare {@code @Port}.
     */
    private Direction portDirectionFromName(String simpleName) {
        if (endsWithAny(simpleName, this.primaryPortSuffixes)) {
            return Direction.PRIMARY;
        }
        return Direction.SECONDARY;
    }

    private Direction adapterDirectionFromName(String simpleName) {
        if (endsWithAny(simpleName, this.primaryAdapterSuffixes)) {
            return Direction.PRIMARY;
        }
        return Direction.SECONDARY;
    }

    private static Direction directionFrom(List<String> segments) {
        if (containsAny(segments, PRIMARY_SEGMENTS)) {
            return Direction.PRIMARY;
        }
        if (containsAny(segments, SECONDARY_SEGMENTS)) {
            return Direction.SECONDARY;
        }
        return null;
    }

    private static boolean containsAny(List<String> segments, Set<String> candidates) {
        return segments.stream().anyMatch(candidates::contains);
    }

    private static boolean endsWithAny(String simpleName, List<String> suffixes) {
        return suffixes.stream().anyMatch(suffix -> !suffix.isEmpty() && simpleName.endsWith(suffix));
    }

    /** Package segments, lower-cased, so {@code com.Acme.Orders.Port.In} still matches. */
    private static List<String> packageSegments(String className) {
        var lastDot = className.lastIndexOf('.');
        if (lastDot <= 0) {
            return List.of();
        }
        return List.of(className.substring(0, lastDot).toLowerCase(Locale.ROOT).split("\\."));
    }

    /** Simple name of a possibly nested class: {@code com.acme.Outer$Inner} yields {@code Inner}. */
    static String simpleName(String className) {
        var name = className.substring(className.lastIndexOf('.') + 1);
        var nested = name.lastIndexOf('$');
        return nested < 0 ? name : name.substring(nested + 1);
    }

}
