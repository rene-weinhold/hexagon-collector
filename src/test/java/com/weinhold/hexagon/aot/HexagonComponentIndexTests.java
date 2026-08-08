package com.weinhold.hexagon.aot;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class HexagonComponentIndexTests {

	@Test
	void rendersSortedSoTheBuildOutputIsReproducible() {
		String content = HexagonComponentIndex.render(List.of("com.acme.Zebra", "com.acme.Apple", "com.acme.Zebra"));

		assertThat(content.lines().filter(line -> !line.startsWith("#")).toList())
				.containsExactly("com.acme.Apple", "com.acme.Zebra");
	}

	@Test
	void readsBackWhatItWrote(@TempDir Path directory) throws Exception {
		writeIndex(directory, HexagonComponentIndex.render(List.of("com.acme.Order", "com.acme.OrderController")));

		HexagonComponentIndex index = HexagonComponentIndex.load(classLoaderFor(directory));

		assertThat(index).isNotNull();
		assertThat(index.getClassNames()).containsExactly("com.acme.Order", "com.acme.OrderController");
	}

	@Test
	void ignoresCommentsAndBlankLines(@TempDir Path directory) throws Exception {
		writeIndex(directory, """
				# a comment
				com.acme.Order

				com.acme.Client   # trailing comment
				""");

		HexagonComponentIndex index = HexagonComponentIndex.load(classLoaderFor(directory));

		assertThat(index.getClassNames()).containsExactly("com.acme.Client", "com.acme.Order");
	}

	@Test
	void isAbsentOnAnOrdinaryClasspath(@TempDir Path directory) throws Exception {
		// The ordinary JVM case: no index, so the caller has to fall back to scanning.
		assertThat(HexagonComponentIndex.load(classLoaderFor(directory))).isNull();
	}

	private static void writeIndex(Path directory, String content) throws Exception {
		Path file = directory.resolve(HexagonComponentIndex.RESOURCE_LOCATION);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	private static ClassLoader classLoaderFor(Path directory) throws Exception {
		return new URLClassLoader(new URL[] { directory.toUri().toURL() }, null);
	}

}
