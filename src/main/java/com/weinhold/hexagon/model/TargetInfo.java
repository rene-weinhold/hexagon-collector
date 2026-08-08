package com.weinhold.hexagon.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Where an {@code OUTBOUND} contact point points to. {@link Resolution} records how the
 * target was determined, so the collector can tell a configured mapping from a discovered
 * service name.
 */
@JsonInclude(NON_EMPTY)
public record TargetInfo(String logicalService, Resolution resolution) {
}
