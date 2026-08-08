package com.weinhold.hexagon;

import javax.sql.DataSource;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.service.annotation.HttpExchange;

import com.weinhold.hexagon.aot.HexagonRuntimeHints;
import com.weinhold.hexagon.contact.AmqpRabbitContactPointDetector;
import com.weinhold.hexagon.contact.ContactPointDetector;
import com.weinhold.hexagon.contact.FeignClientContactPointDetector;
import com.weinhold.hexagon.contact.HttpExchangeOutboundContactPointDetector;
import com.weinhold.hexagon.contact.JdbcDataSourceContactPointDetector;
import com.weinhold.hexagon.contact.KafkaListenerContactPointDetector;
import com.weinhold.hexagon.contact.SpringWebInboundContactPointDetector;
import com.weinhold.hexagon.contact.WebFluxInboundContactPointDetector;

/**
 * Autoconfiguration for the Hexagon collection starter. Registers the lazy
 * {@link HexagonDescriptorFactory} and the {@link HexagonEndpoint} at
 * {@code /actuator/hexagon}, plus a {@link ContactPointDetector} per technology found on the
 * classpath.
 * <p>Detectors are explicitly ordered. The first one to contribute names the adapter's
 * {@code technology}, and an adapter can legitimately match several — a controller that is
 * also a Kafka listener, say — so leaving that to bean-definition order would make the
 * reported technology depend on classpath accidents.
 */
// The JDBC detector asks whether a DataSource bean exists, so it has to be registered after
// whichever auto-configuration would have defined one. Names not present are ignored, which
// is what lets one entry cover both the Boot 3 and Boot 4 package layouts.
@AutoConfiguration(afterName = { "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration" })
@ConditionalOnClass(Endpoint.class)
@EnableConfigurationProperties(HexagonCollectionProperties.class)
@ConditionalOnProperty(prefix = "hexagon.collection", name = "enabled", matchIfMissing = true)
@ImportRuntimeHints(HexagonRuntimeHints.class)
public class HexagonCollectionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HexagonDescriptorFactory hexagonDescriptorFactory(BeanFactory beanFactory, Environment environment,
        HexagonCollectionProperties properties, ObjectProvider<BuildProperties> buildProperties,
        ObjectProvider<ContactPointDetector> detectors) {
        return new HexagonDescriptorFactory(beanFactory, environment, properties, buildProperties,
            detectors.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public HexagonEndpoint hexagonEndpoint(HexagonDescriptorFactory hexagonDescriptorFactory) {
        return new HexagonEndpoint(hexagonDescriptorFactory);
    }

    /** HTTP inbound routes on the servlet stack, read from Spring MVC's handler mapping. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class)
    @ConditionalOnWebApplication(type = Type.SERVLET)
    static class SpringWebDetectorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @Order(10)
        SpringWebInboundContactPointDetector springWebInboundContactPointDetector(
            ObjectProvider<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping> handlerMappings) {
            return new SpringWebInboundContactPointDetector(handlerMappings);
        }

    }

    /** The same, for the reactive stack. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping.class)
    @ConditionalOnWebApplication(type = Type.REACTIVE)
    static class WebFluxDetectorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @Order(20)
        WebFluxInboundContactPointDetector webFluxInboundContactPointDetector(
            ObjectProvider<org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping> handlerMappings) {
            return new WebFluxInboundContactPointDetector(handlerMappings);
        }

    }

    /**
     * Outbound HTTP calls made through declarative {@code @HttpExchange} interface clients.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HttpExchange.class)
    static class HttpExchangeDetectorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @Order(30)
        HttpExchangeOutboundContactPointDetector httpExchangeOutboundContactPointDetector(Environment environment,
            HexagonCollectionProperties properties) {
            return new HttpExchangeOutboundContactPointDetector(environment, properties.getTargets());
        }

    }

    /** Outbound HTTP calls made through {@code @FeignClient} interfaces. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
    static class FeignDetectorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @Order(40)
        FeignClientContactPointDetector feignClientContactPointDetector(Environment environment,
            HexagonCollectionProperties properties) {
            return new FeignClientContactPointDetector(environment, properties.getTargets());
        }

    }

    /** Kafka topics consumed via {@code @KafkaListener} and produced via {@code @SendTo}. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KafkaListener.class)
    static class KafkaDetectorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @Order(50)
        KafkaListenerContactPointDetector kafkaListenerContactPointDetector(Environment environment) {
            return new KafkaListenerContactPointDetector(environment);
        }

    }

    /** AMQP exchanges/queues consumed via {@code @RabbitListener} and produced via {@code @SendTo}. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RabbitListener.class)
    static class AmqpDetectorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @Order(60)
        AmqpRabbitContactPointDetector amqpRabbitContactPointDetector(Environment environment) {
            return new AmqpRabbitContactPointDetector(environment);
        }

    }

    /**
     * The service's database. {@link DataSource} lives in the JDK, so gating on the class
     * alone would register this detector in every application ever; it is the presence of an
     * actual data source or URL that says there is a database to report.
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(JdbcDetectorConfiguration.OnDatabase.class)
    static class JdbcDetectorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @Order(70)
        JdbcDataSourceContactPointDetector jdbcDataSourceContactPointDetector(ObjectProvider<DataSource> dataSources,
            Environment environment) {
            return new JdbcDataSourceContactPointDetector(dataSources, environment);
        }

        static class OnDatabase extends AnyNestedCondition {

            OnDatabase() {
                super(ConfigurationPhase.REGISTER_BEAN);
            }

            @ConditionalOnBean(DataSource.class)
            static class DataSourceBean {

            }

            @ConditionalOnProperty(name = "spring.datasource.url")
            static class DataSourceUrl {

            }

        }

    }

}
