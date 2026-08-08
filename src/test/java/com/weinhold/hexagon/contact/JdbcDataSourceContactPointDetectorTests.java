package com.weinhold.hexagon.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import com.weinhold.hexagon.contact.ContactPointDetector.AdapterContext;
import com.weinhold.hexagon.contact.ContactPointDetector.Contribution;
import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.Confidence;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.Direction;
import com.weinhold.hexagon.model.Provenance;

class JdbcDataSourceContactPointDetectorTests {

    @Test
    void reportsDatabaseForDeclaredRepositories() {
        MockEnvironment environment =
            new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/orders");
        JdbcDataSourceContactPointDetector detector = new JdbcDataSourceContactPointDetector(noDataSource(), environment);

        Contribution contribution = detector.detect(context(DeclaredOrderRepository.class));

        assertThat(contribution.technology()).isEqualTo("jdbc");
        assertThat(contribution.contactPoints()).singleElement().satisfies(point -> {
            assertThat(point.key()).isEqualTo("jdbc:postgresql/orders");
            assertThat(point.direction()).isEqualTo(ContactDirection.OUTBOUND);
            assertThat(point.confidence()).isEqualTo(Confidence.HIGH);
            assertThat(point.attributes()).containsEntry("vendor", "postgresql").containsEntry("database", "orders");
        });
    }

    @Test
    void downgradesConfidenceWhenTheAdapterWasOnlyMatchedByName() {
        MockEnvironment environment =
            new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/orders");
        JdbcDataSourceContactPointDetector detector = new JdbcDataSourceContactPointDetector(noDataSource(), environment);

        Contribution contribution = detector.detect(context(JpaOrderRepository.class));

        // The class merely ends in "Repository". A shared-database finding is one of the most
        // consequential things the collector reports, so a guess must not arrive as HIGH.
        assertThat(contribution.contactPoints()).singleElement()
                                                .satisfies(point -> assertThat(point.confidence()).isEqualTo(Confidence.MEDIUM));
    }

    @Test
    void dropsToLowWhenNeitherTheDatabaseNorTheAdapterIsCertain() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:h2:mem:");
        JdbcDataSourceContactPointDetector detector = new JdbcDataSourceContactPointDetector(noDataSource(), environment);

        Contribution contribution = detector.detect(context(JpaOrderRepository.class));

        assertThat(contribution.contactPoints()).singleElement().satisfies(point -> {
            assertThat(point.key()).isEqualTo("jdbc:h2");
            assertThat(point.confidence()).isEqualTo(Confidence.LOW);
        });
    }

    @Test
    void keepsCredentialsOutOfTheContactPoint() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url",
            "jdbc:postgresql://orders_app:s3cr3t@db.internal:5432/orders?ssl=true&password=s3cr3t");
        JdbcDataSourceContactPointDetector detector = new JdbcDataSourceContactPointDetector(noDataSource(), environment);

        Contribution contribution = detector.detect(context(DeclaredOrderRepository.class));

        assertThat(contribution.contactPoints()).singleElement().satisfies(point -> {
            assertThat(point.key()).isEqualTo("jdbc:postgresql/orders");
            assertThat(point.attributes()).containsOnlyKeys("vendor", "database");
        });
        // The endpoint is potentially exposed: nothing may leave that could not also appear
        // in an architecture diagram (contract principle 5).
        assertThat(contribution.contactPoints().toString()).doesNotContain("s3cr3t")
                                                           .doesNotContain("orders_app")
                                                           .doesNotContain("db.internal");
    }

    @Test
    void ignoresAdaptersThatAreNotPersistence() {
        MockEnvironment environment =
            new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/orders");
        JdbcDataSourceContactPointDetector detector = new JdbcDataSourceContactPointDetector(noDataSource(), environment);

        assertThat(detector.detect(context(OrderRestController.class)).isEmpty()).isTrue();
    }

    @Test
    void contributesNothingWithoutADatabase() {
        JdbcDataSourceContactPointDetector detector =
            new JdbcDataSourceContactPointDetector(noDataSource(), new MockEnvironment());

        assertThat(detector.detect(context(JpaOrderRepository.class)).isEmpty()).isTrue();
    }

    private static ObjectProvider<DataSource> noDataSource() {
        return new ObjectProvider<>() {

            @Override
            public DataSource getObject() {
                throw new NoSuchBeanDefinitionException(DataSource.class);
            }

            @Override
            public DataSource getObject(Object... args) {
                throw new NoSuchBeanDefinitionException(DataSource.class);
            }

            @Override
            public DataSource getIfAvailable() {
                return null;
            }

            @Override
            public DataSource getIfUnique() {
                return null;
            }
        };
    }

    private static AdapterContext context(Class<?> type) {
        AdapterInfo base = new AdapterInfo(type.getName(), type.getSimpleName(), Direction.SECONDARY, null, Provenance.ANNOTATION,
            List.of(), List.of());
        return new AdapterContext(type, base);
    }

    /** Declares what it is, so the database can be attached to it with full confidence. */
    @org.jmolecules.ddd.annotation.Repository
    static class DeclaredOrderRepository {
    }

    /** Recognizable only by its name. */
    static class JpaOrderRepository {
    }

    static class OrderRestController {
    }

}
