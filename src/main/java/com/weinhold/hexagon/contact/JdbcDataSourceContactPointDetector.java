package com.weinhold.hexagon.contact;

import static com.weinhold.hexagon.contact.CanonicalKey.jdbc;
import static com.weinhold.hexagon.model.Confidence.HIGH;
import static com.weinhold.hexagon.model.Confidence.MEDIUM;
import static com.weinhold.hexagon.model.ContactDirection.OUTBOUND;
import static com.weinhold.hexagon.model.Protocol.JDBC;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import com.weinhold.hexagon.model.Confidence;
import com.weinhold.hexagon.model.ContactPointInfo;

/**
 * Reports the service's database as an {@code OUTBOUND} {@code jdbc:{vendor}/{database}}
 * contact point, attached to persistence adapters. The URL is read from
 * {@code spring.datasource.url} when present (no connection opened); otherwise it falls back
 * to the {@link DataSource}'s connection metadata. This is the touchpoint the collector uses
 * to surface shared databases across services.
 * <p>Only the vendor and database name leave the process: host, port, credentials and
 * connection properties are dropped by {@link JdbcUrl} (contract principle 5).
 */
public class JdbcDataSourceContactPointDetector implements ContactPointDetector {

    private static final Logger log = LoggerFactory.getLogger(JdbcDataSourceContactPointDetector.class);

    private static final String JMOLECULES_REPOSITORY = "org.jmolecules.ddd.annotation.Repository";

    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";

    private final ObjectProvider<DataSource> dataSources;

    private final Environment environment;

    private volatile boolean computed;

    private volatile JdbcUrl url;

    public JdbcDataSourceContactPointDetector(ObjectProvider<DataSource> dataSources, Environment environment) {
        this.dataSources = dataSources;
        this.environment = environment;
    }

    /** How firmly the adapter was identified as the thing that talks to the database. */
    private enum Match {

        /** Declared: {@code @Repository} (jMolecules) or a Spring Data repository type. */
        DECLARED,

        /** Guessed from the class name alone. */
        CONVENTION,

        /** Not a persistence adapter. */
        NONE

    }

    @Override
    public Contribution detect(AdapterContext adapter) {
        var match = match(adapter.type());
        if (match == Match.NONE) {
            return Contribution.none();
        }
        var parsed = jdbcUrl();
        if (parsed == null) {
            return Contribution.none();
        }
        var point = new ContactPointInfo(jdbc(parsed.vendor(), parsed.database()), JDBC, OUTBOUND,
            confidence(parsed, match), null, attributes(parsed));
        return new Contribution("jdbc", List.of(point));
    }

    private JdbcUrl jdbcUrl() {
        if (!this.computed) {
            synchronized (this) {
                if (!this.computed) {
                    this.url = compute();
                    this.computed = true;
                }
            }
        }
        return this.url;
    }

    private JdbcUrl compute() {
        var configured = resolveUrl();
        if (configured == null) {
            return null;
        }
        var parsed = JdbcUrl.parse(configured);
        return (parsed == null || parsed.vendor() == null) ? null : parsed;
    }

    /**
     * A database we could not name is one guess; an adapter we only recognized by its class
     * name is another. Publishing a {@code jdbc:} key with {@code HIGH} confidence off the
     * back of a naming convention is exactly the guess-dressed-as-fact the contract forbids —
     * and this key is the one the collector uses to claim two services share a database.
     */
    private static Confidence confidence(JdbcUrl parsed, Match match) {
        var confidence = parsed.database() != null ? HIGH : MEDIUM;
        return match == Match.CONVENTION ? confidence.downgrade() : confidence;
    }

    private static Map<String, Object> attributes(JdbcUrl parsed) {
        var attributes = new LinkedHashMap<String, Object>();
        attributes.put("vendor", parsed.vendor());
        if (parsed.database() != null) {
            attributes.put("database", parsed.database());
        }
        return attributes;
    }

    private String resolveUrl() {
        var configured = this.environment.getProperty("spring.datasource.url");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        var dataSource = this.dataSources.getIfAvailable();
        if (dataSource == null) {
            return null;
        }
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        } catch (Exception ex) {
            log.debug("Could not read JDBC URL from DataSource metadata: {}", ex.toString());
            return null;
        }
    }

    private Match match(Class<?> type) {
        var annotations = MergedAnnotations.from(type);
        var classLoader = type.getClassLoader();
        if (isAnnotationPresent(annotations, JMOLECULES_REPOSITORY, classLoader)) {
            return Match.DECLARED;
        }
        if (ClassUtils.isPresent(SPRING_DATA_REPOSITORY, classLoader)) {
            var repository = ClassUtils.resolveClassName(SPRING_DATA_REPOSITORY, classLoader);
            if (repository.isAssignableFrom(type)) {
                return Match.DECLARED;
            }
        }
        var simpleName = type.getSimpleName();
        if (simpleName.endsWith("Repository") || simpleName.endsWith("Persistence") || simpleName.endsWith("Dao")) {
            return Match.CONVENTION;
        }
        return Match.NONE;
    }

    @SuppressWarnings("unchecked")
    private static boolean isAnnotationPresent(MergedAnnotations annotations, String annotationName, ClassLoader classLoader) {
        if (!ClassUtils.isPresent(annotationName, classLoader)) {
            return false;
        }
        return annotations.isPresent((Class<? extends Annotation>) ClassUtils.resolveClassName(annotationName, classLoader));
    }

}
