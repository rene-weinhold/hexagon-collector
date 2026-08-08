package com.weinhold.hexagon.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The domain core of the hexagon — "what is inside". Populated from jMolecules DDD and
 * event annotations. {@code basePackage} is derived from the discovered components.
 */
@JsonInclude(NON_EMPTY)
public record CoreInfo(String basePackage, List<ComponentInfo> aggregates, EventsInfo events) {
}
