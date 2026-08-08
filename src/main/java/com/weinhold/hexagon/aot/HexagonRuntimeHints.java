package com.weinhold.hexagon.aot;

import java.util.List;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import com.weinhold.hexagon.model.AdapterInfo;
import com.weinhold.hexagon.model.ComponentInfo;
import com.weinhold.hexagon.model.Confidence;
import com.weinhold.hexagon.model.ContactDirection;
import com.weinhold.hexagon.model.ContactPointInfo;
import com.weinhold.hexagon.model.CoreInfo;
import com.weinhold.hexagon.model.Direction;
import com.weinhold.hexagon.model.EventsInfo;
import com.weinhold.hexagon.model.HexagonDescriptor;
import com.weinhold.hexagon.model.PortInfo;
import com.weinhold.hexagon.model.Protocol;
import com.weinhold.hexagon.model.Provenance;
import com.weinhold.hexagon.model.Resolution;
import com.weinhold.hexagon.model.ServiceInfo;
import com.weinhold.hexagon.model.TargetInfo;

/**
 * Reflection hints for the payload records, so Jackson can serialize the descriptor in a
 * native image, plus the resource hint for the build-time component index.
 * <p>These are the types the endpoint's JSON is made of; without the hints the endpoint
 * compiles and starts perfectly and then fails at the moment somebody actually reads it.
 */
public class HexagonRuntimeHints implements RuntimeHintsRegistrar {

    private static final List<Class<?>> PAYLOAD_TYPES = List.of(HexagonDescriptor.class, ServiceInfo.class, CoreInfo.class,
        EventsInfo.class, ComponentInfo.class, PortInfo.class, AdapterInfo.class, ContactPointInfo.class, TargetInfo.class,
        Direction.class, ContactDirection.class, Confidence.class, Protocol.class, Provenance.class, Resolution.class);

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (var type : PAYLOAD_TYPES) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
        hints.resources().registerPattern(HexagonComponentIndex.RESOURCE_LOCATION);
    }

}
