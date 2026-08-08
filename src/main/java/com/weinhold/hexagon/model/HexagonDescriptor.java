package com.weinhold.hexagon.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Root payload of {@code /actuator/hexagon}. Computed once at startup and served
 * unchanged afterward. This describes how the service is built, not how it is doing.
 */
@JsonInclude(NON_EMPTY)
public record HexagonDescriptor(String schemaVersion, Instant generatedAt, ServiceInfo service, CoreInfo core,
        List<PortInfo> ports, List<AdapterInfo> adapters) {

    /** The schema version this starter produces. */
    public static final String SCHEMA_VERSION = "1.0.0";

}
