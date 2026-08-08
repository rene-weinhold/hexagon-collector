package com.weinhold.hexagon.contact;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.weinhold.hexagon.model.Confidence;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.Protocol;
import com.weinhold.hexagon.model.Resolution;
import com.weinhold.hexagon.model.TargetInfo;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Reports the calls a declarative HTTP interface client ({@code @HttpExchange}) makes as
 * {@code OUTBOUND} HTTP contact points. This is the tractable, declarative slice of outbound
 * HTTP — imperative {@code RestClient}/{@code WebClient} calls carry no static metadata and
 * are deliberately not guessed.
 *
 * <p>Declarative clients carry no target host, so the target service is resolved from the
 * {@code hexagon.collection.targets} map (keyed by adapter class name). When mapped, the key
 * is {@code http:{service}:{METHOD} {path}} (HIGH); otherwise it falls back to
 * {@code http:{METHOD} {path}} for the collector to resolve by path, one confidence step
 * lower. An unspecified HTTP method or an unresolvable placeholder in the path costs a
 * further step each.
 */
public class HttpExchangeOutboundContactPointDetector implements ContactPointDetector {

	private final Environment environment;

	private final Map<String, String> targets;

	public HttpExchangeOutboundContactPointDetector(Environment environment, Map<String, String> targets) {
		this.environment = environment;
		this.targets = targets;
	}

	@Override
	public Contribution detect(AdapterContext adapter) {
		Class<?> type = adapter.type();
		HttpExchange typeExchange = AnnotatedElementUtils.findMergedAnnotation(type, HttpExchange.class);
		String basePath = typeExchange != null ? url(typeExchange) : "";
		String targetService = this.targets.get(type.getName());

		Map<String, ContactPointInfo> byKey = new LinkedHashMap<>();
		for (Method method : exchangeMethods(type)) {
			HttpExchange exchange = AnnotatedElementUtils.findMergedAnnotation(method, HttpExchange.class);
			if (exchange == null) {
				continue;
			}
			ContactPointInfo point = toContactPoint(exchange, basePath, targetService);
			byKey.putIfAbsent(point.key(), point);
		}

		if (byKey.isEmpty()) {
			return Contribution.none();
		}
		return new Contribution("spring-http-interface", new ArrayList<>(byKey.values()));
	}

	private ContactPointInfo toContactPoint(HttpExchange exchange, String basePath, String targetService) {
		String rawMethod = exchange.method();
		String httpMethod = StringUtils.hasText(rawMethod) ? rawMethod : "*";
		String path = Placeholders.resolve(this.environment, join(basePath, url(exchange)));

		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("method", CanonicalKey.normalizeMethod(httpMethod));
		attributes.put("pathTemplate", CanonicalKey.normalizePath(path));

		// Every unknown that went into the key costs one step of confidence: an unspecified
		// HTTP method, a path we could not fully resolve, and — for an unmapped adapter — a
		// target the collector still has to work out by matching the path.
		Confidence confidence = Confidence.HIGH;
		if (!StringUtils.hasText(rawMethod)) {
			confidence = confidence.downgrade();
		}
		if (Placeholders.isUnresolved(path)) {
			confidence = confidence.downgrade();
		}

		if (StringUtils.hasText(targetService)) {
			attributes.put("targetService", targetService);
			return new ContactPointInfo(CanonicalKey.httpOutbound(targetService, httpMethod, path), Protocol.HTTP,
					ContactDirection.OUTBOUND, confidence, new TargetInfo(targetService, Resolution.CONFIG), attributes);
		}
		return new ContactPointInfo(CanonicalKey.http(httpMethod, path), Protocol.HTTP, ContactDirection.OUTBOUND,
				confidence.downgrade(), null, attributes);
	}

	private static Set<Method> exchangeMethods(Class<?> type) {
		Set<Method> methods = new LinkedHashSet<>(List.of(type.getMethods()));
		for (Class<?> implemented : ClassUtils.getAllInterfacesForClass(type)) {
			methods.addAll(List.of(implemented.getMethods()));
		}
		return methods;
	}

	private static String url(HttpExchange exchange) {
		return StringUtils.hasText(exchange.url()) ? exchange.url() : exchange.value();
	}

	private static String join(String base, String path) {
		if (!StringUtils.hasText(base)) {
			return path;
		}
		if (!StringUtils.hasText(path)) {
			return base;
		}
		return base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
	}

}
