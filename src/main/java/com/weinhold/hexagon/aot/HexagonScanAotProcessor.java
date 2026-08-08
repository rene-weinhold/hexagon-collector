package com.weinhold.hexagon.aot;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ClassUtils;

import com.weinhold.hexagon.HexagonCollectionProperties;
import com.weinhold.hexagon.HexagonConventions;
import com.weinhold.hexagon.HexagonScanner;

/**
 * Runs the hexagon scan at build time and leaves the result in the application, so the
 * endpoint still works after native compilation.
 * <p>This is the answer to the contract's open decision 2 — scan at startup or at build time.
 * The choice made here is <em>both</em>: startup scanning stays the default because it needs
 * no build setup and picks up whatever is actually on the classpath, and the build-time index
 * is written only when Spring's AOT processing runs, which is exactly when runtime scanning
 * would otherwise stop working. Nothing about the JVM behaviour changes; native images gain a
 * working endpoint instead of an empty one.
 * <p>Only the class <em>names</em> are recorded, not the finished descriptor: contact points
 * come from live framework beans (handler mappings, listener registries, the data source) and
 * must still be read at runtime.
 */
public class HexagonScanAotProcessor implements BeanFactoryInitializationAotProcessor {

    private static final Logger log = LoggerFactory.getLogger(HexagonScanAotProcessor.class);

    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        var properties = bindProperties(beanFactory);
        if (!properties.isEnabled()) {
            return null;
        }
        var basePackages = resolveBasePackages(beanFactory, properties);
        if (basePackages.isEmpty()) {
            return null;
        }

        var classLoader =
            beanFactory.getBeanClassLoader() != null ? beanFactory.getBeanClassLoader() : ClassUtils.getDefaultClassLoader();
        var classNames = new HexagonScanner(basePackages, classLoader,
            new HexagonConventions(properties.getConventions())).relevantClassNames();
        if (classNames.isEmpty()) {
            return null;
        }
        log.info("Hexagon AOT: indexed {} candidate types into {}", classNames.size(), HexagonComponentIndex.RESOURCE_LOCATION);
        return (generationContext, beanFactoryInitializationCode) -> contribute(generationContext, classNames);
    }

    /**
     * Writes the index and registers the hints the runtime needs to use it: the resource
     * itself, and reflective access to every indexed type so its annotations, methods and
     * interfaces can still be read once the classpath is gone.
     */
    public static void contribute(GenerationContext generationContext, List<String> classNames) {
        generationContext.getGeneratedFiles()
                         .addResourceFile(HexagonComponentIndex.RESOURCE_LOCATION, HexagonComponentIndex.render(classNames));

        var hints = generationContext.getRuntimeHints();
        hints.resources().registerPattern(HexagonComponentIndex.RESOURCE_LOCATION);
        for (var className : classNames) {
            hints.reflection()
                 .registerType(TypeReference.of(className), MemberCategory.INTROSPECT_PUBLIC_METHODS,
                     MemberCategory.INTROSPECT_DECLARED_METHODS);
        }
    }

    private static HexagonCollectionProperties bindProperties(ConfigurableListableBeanFactory beanFactory) {
        var existing = beanFactory.getBeanProvider(HexagonCollectionProperties.class).getIfAvailable();
        if (existing != null) {
            return existing;
        }
        var environment = beanFactory.getBeanProvider(ConfigurableEnvironment.class).getIfAvailable();
        if (environment == null) {
            return new HexagonCollectionProperties();
        }
        return Binder.get(environment)
                     .bind("hexagon.collection", HexagonCollectionProperties.class)
                     .orElseGet(HexagonCollectionProperties::new);
    }

    private static List<String> resolveBasePackages(ConfigurableListableBeanFactory beanFactory,
        HexagonCollectionProperties properties) {
        if (!properties.getBasePackages().isEmpty()) {
            return properties.getBasePackages();
        }
        return AutoConfigurationPackages.has(beanFactory) ? AutoConfigurationPackages.get(beanFactory) : List.of();
    }

}
