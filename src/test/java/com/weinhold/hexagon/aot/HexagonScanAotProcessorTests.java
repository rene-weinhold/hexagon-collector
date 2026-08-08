package com.weinhold.hexagon.aot;

import java.util.List;

import com.weinhold.hexagon.HexagonConventions;
import com.weinhold.hexagon.HexagonScanner;
import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.javapoet.ClassName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The build-time half of native-image support: the scan runs while classpath scanning still
 * works, and its answer is written into the application.
 */
class HexagonScanAotProcessorTests {

	private final InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();

	private final DefaultGenerationContext generationContext = new DefaultGenerationContext(
			new ClassNameGenerator(ClassName.get("com.weinhold.hexagon", "Test")), this.generatedFiles);

	@Test
	void writesTheIndexAndTheHintsNeededToReadItBack() throws Exception {
		HexagonScanAotProcessor.contribute(this.generationContext, List.of("com.acme.Order", "com.acme.OrderClient"));

		String written = this.generatedFiles.getGeneratedFileContent(Kind.RESOURCE,
				HexagonComponentIndex.RESOURCE_LOCATION);
		assertThat(written).contains("com.acme.Order").contains("com.acme.OrderClient");

		var hints = this.generationContext.getRuntimeHints();
		assertThat(RuntimeHintsPredicates.resource().forResource(HexagonComponentIndex.RESOURCE_LOCATION))
				.accepts(hints);
		// The index only carries names; reading their annotations and methods after native
		// compilation still needs reflective access to have been declared.
		assertThat(hints.reflection().typeHints()).extracting(hint -> hint.getType().getCanonicalName())
				.contains("com.acme.Order", "com.acme.OrderClient");
	}

	@Test
	void indexesEverythingEitherPassWouldClassify() {
		List<String> classNames = new HexagonScanner(List.of("com.weinhold.hexagon.conventions"),
				getClass().getClassLoader(), HexagonConventions.defaults()).relevantClassNames();

		assertThat(classNames).contains("com.weinhold.hexagon.conventions.application.port.in.PlaceOrderUseCase",
				"com.weinhold.hexagon.conventions.adapter.in.web.OrderWebController",
				"com.weinhold.hexagon.conventions.domain.event.OrderShipped");
		// A value object no pass would classify is not worth a reflection hint.
		assertThat(classNames).doesNotContain("com.weinhold.hexagon.conventions.domain.Money");
	}

	@Test
	void indexesTypesCarryingListenerMethodsSoConsumedEventsSurviveNativeCompilation() {
		List<String> classNames = new HexagonScanner(List.of("com.weinhold.hexagon.listener"),
				getClass().getClassLoader(), HexagonConventions.defaults()).relevantClassNames();

		assertThat(classNames).contains("com.weinhold.hexagon.listener.ShipmentEventListener");
	}

	@Test
	void contributesNothingWhenThereIsNothingToScan() {
		assertThat(new HexagonScanAotProcessor().processAheadOfTime(new DefaultListableBeanFactory())).isNull();
	}

}
