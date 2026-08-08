package com.weinhold.hexagon.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A domain building block referenced by the {@code core} block — an aggregate or a domain
 * event. {@code id} is the FQCN; {@code name} defaults to the simple class name.
 */
@JsonInclude(NON_EMPTY)
public record ComponentInfo(String id, String name, Provenance provenance) {
}
