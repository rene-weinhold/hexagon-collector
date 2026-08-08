package com.weinhold.hexagon;

import java.io.PrintWriter;
import java.sql.Connection;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.weinhold.hexagon.contact.JdbcDataSourceContactPointDetector;
import com.weinhold.hexagon.model.HexagonDescriptor;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class HexagonCollectionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(HexagonCollectionAutoConfiguration.class));

	@Test
	void registersFactoryAndEndpointByDefault() {
		this.contextRunner.run(context -> assertThat(context)
				.hasSingleBean(HexagonDescriptorFactory.class)
				.hasSingleBean(HexagonEndpoint.class));
	}

	@Test
	void autoConfigurationCanBeDisabled() {
		this.contextRunner.withPropertyValues("hexagon.collection.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(HexagonEndpoint.class));
	}

	@Test
	void scansConfiguredBasePackagesAndPopulatesService() {
		this.contextRunner
				.withPropertyValues("spring.application.name=orders-service",
						"hexagon.collection.base-packages=com.weinhold.hexagon.sample",
						"hexagon.collection.service.environment=prod")
				.run(context -> {
					HexagonDescriptor descriptor = context.getBean(HexagonEndpoint.class).hexagon();
					assertThat(descriptor.schemaVersion()).isEqualTo("1.0.0");
					assertThat(descriptor.generatedAt()).isNotNull();
					assertThat(descriptor.service().id()).isEqualTo("orders-service");
					assertThat(descriptor.service().environment()).isEqualTo("prod");
					assertThat(descriptor.service().basePackage()).isEqualTo("com.weinhold.hexagon.sample");
					assertThat(descriptor.ports()).hasSize(2);
					assertThat(descriptor.adapters()).hasSize(2);
					assertThat(descriptor.core()).isNotNull();
					assertThat(descriptor.core().aggregates()).hasSize(1);
					assertThat(descriptor.core().events().published()).hasSize(1);
				});
	}

	@Test
	void skipsTheJdbcDetectorWhenThereIsNoDatabase() {
		// javax.sql.DataSource lives in the JDK, so gating on the class alone would have
		// registered this detector in every application ever built.
		this.contextRunner
				.run(context -> assertThat(context).doesNotHaveBean(JdbcDataSourceContactPointDetector.class));
	}

	@Test
	void registersTheJdbcDetectorForAConfiguredUrl() {
		this.contextRunner.withPropertyValues("spring.datasource.url=jdbc:postgresql://db:5432/orders")
				.run(context -> assertThat(context).hasSingleBean(JdbcDataSourceContactPointDetector.class));
	}

	@Test
	void registersTheJdbcDetectorForADataSourceBean() {
		this.contextRunner.withBean(DataSource.class, StubDataSource::new)
				.run(context -> assertThat(context).hasSingleBean(JdbcDataSourceContactPointDetector.class));
	}

	/**
	 * Enough of a {@link DataSource} to be registered as one. Nothing here is ever called:
	 * the point is only that a database exists, which is what the condition asks.
	 */
	static class StubDataSource implements DataSource {

		@Override
		public Connection getConnection() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Connection getConnection(String username, String password) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PrintWriter getLogWriter() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setLogWriter(PrintWriter out) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setLoginTimeout(int seconds) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getLoginTimeout() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Logger getParentLogger() {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> T unwrap(Class<T> iface) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isWrapperFor(Class<?> iface) {
			return false;
		}

	}

}
