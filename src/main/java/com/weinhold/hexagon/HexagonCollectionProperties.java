package com.weinhold.hexagon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Hexagon collection starter.
 * <p>Bind these under the {@code hexagon.collection} prefix, e.g. in
 * {@code application.yaml}:
 *
 * <pre>
 * hexagon:
 *   collection:
 *     enabled: true
 *     base-packages: [com.acme.orders]
 *     service:
 *       display-name: Orders
 *       environment: prod
 * </pre>
 */
@ConfigurationProperties(prefix = "hexagon.collection")
public class HexagonCollectionProperties {

    /**
     * Whether the Hexagon collection autoconfiguration is enabled.
     */
    private boolean enabled = true;

    /**
     * Packages to scan for jMolecules-annotated ports and adapters. When empty, the
     * application's autoconfiguration base packages are used.
     */
    private List<String> basePackages = new ArrayList<>();

    /**
     * Descriptive metadata about the service reported in the endpoint's {@code service}
     * block. All values are optional.
     */
    private final Service service = new Service();

    /**
     * Maps an outbound adapter (by fully-qualified class name) to the logical service it
     * calls, so outbound HTTP contact points can be resolved to a target. When an adapter is
     * not mapped, its outbound HTTP contact point is left unresolved for the collector.
     */
    private Map<String, String> targets = new LinkedHashMap<>();

    /**
     * Package and class-name conventions used to describe services that carry no jMolecules
     * annotations. Anything found this way is reported with {@code provenance: CONVENTION}.
     */
    private final Conventions conventions = new Conventions();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBasePackages() {
        return this.basePackages;
    }

    public void setBasePackages(List<String> basePackages) {
        this.basePackages = basePackages;
    }

    public Service getService() {
        return this.service;
    }

    public Map<String, String> getTargets() {
        return this.targets;
    }

    public void setTargets(Map<String, String> targets) {
        this.targets = targets;
    }

    public Conventions getConventions() {
        return this.conventions;
    }

    /**
     * Optional overrides for the {@code service} block of the contract.
     */
    public static class Service {

        /**
         * Stable logical service id. Defaults to {@code spring.application.name}.
         */
        private String id;

        /**
         * Human-friendly name for display.
         */
        private String displayName;

        /**
         * Service version. Defaults to the build information version when available.
         */
        private String version;

        /**
         * Environment the service runs in (e.g. {@code prod}), used to separate landscapes.
         */
        private String environment;

        /**
         * Identifies this instance among several of the same service, so a collector polling
         * every pod can deduplicate. Defaults to {@code spring.application.instance-id}, then
         * to the {@code HOSTNAME} environment variable (the pod name, under Kubernetes).
         */
        private String instanceId;

        /**
         * Source repository URL.
         */
        private String repository;

        public String getId() {
            return this.id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getVersion() {
            return this.version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getEnvironment() {
            return this.environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getInstanceId() {
            return this.instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        public String getRepository() {
            return this.repository;
        }

        public void setRepository(String repository) {
            this.repository = repository;
        }

    }

    /**
     * Package and class-name conventions used to describe an un-annotated service. Every
     * suffix list is a plain override, so a team whose driving adapters are called
     * {@code *Facade} just adds that to {@code primary-adapter-suffixes}.
     *
     * @see HexagonConventions
     */
    public static class Conventions {

        /**
         * Whether to fall back to package and class-name conventions for types that carry no
         * jMolecules annotation.
         */
        private boolean enabled = true;

        /** Interface name suffixes that mark a driving (primary) port. */
        private List<String> primaryPortSuffixes = new ArrayList<>(List.of("UseCase", "UseCases", "InboundPort"));

        /** Interface name suffixes that mark a driven (secondary) port. */
        private List<String> secondaryPortSuffixes = new ArrayList<>(
                List.of("Port", "OutboundPort", "Gateway", "Repository"));

        /** Class name suffixes that mark a driving (primary) adapter. */
        private List<String> primaryAdapterSuffixes = new ArrayList<>(
                List.of("Controller", "Resource", "Endpoint", "Listener", "Consumer", "Subscriber"));

        /** Class name suffixes that mark a driven (secondary) adapter. */
        private List<String> secondaryAdapterSuffixes = new ArrayList<>(
                List.of("Adapter", "Client", "Gateway", "Repository", "RepositoryImpl", "Dao", "Publisher", "Producer"));

        /** Class name suffixes that mark a domain event. */
        private List<String> eventSuffixes = new ArrayList<>(List.of("Event"));

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getPrimaryPortSuffixes() {
            return this.primaryPortSuffixes;
        }

        public void setPrimaryPortSuffixes(List<String> primaryPortSuffixes) {
            this.primaryPortSuffixes = primaryPortSuffixes;
        }

        public List<String> getSecondaryPortSuffixes() {
            return this.secondaryPortSuffixes;
        }

        public void setSecondaryPortSuffixes(List<String> secondaryPortSuffixes) {
            this.secondaryPortSuffixes = secondaryPortSuffixes;
        }

        public List<String> getPrimaryAdapterSuffixes() {
            return this.primaryAdapterSuffixes;
        }

        public void setPrimaryAdapterSuffixes(List<String> primaryAdapterSuffixes) {
            this.primaryAdapterSuffixes = primaryAdapterSuffixes;
        }

        public List<String> getSecondaryAdapterSuffixes() {
            return this.secondaryAdapterSuffixes;
        }

        public void setSecondaryAdapterSuffixes(List<String> secondaryAdapterSuffixes) {
            this.secondaryAdapterSuffixes = secondaryAdapterSuffixes;
        }

        public List<String> getEventSuffixes() {
            return this.eventSuffixes;
        }

        public void setEventSuffixes(List<String> eventSuffixes) {
            this.eventSuffixes = eventSuffixes;
        }

    }

}
