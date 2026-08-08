package com.weinhold.hexagon;

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

}
