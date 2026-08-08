package com.weinhold.hexagon.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A port: a hole in the hexagon. {@code id} (FQCN) and {@code direction} are required;
 * the rest is optional.
 */
@JsonInclude(NON_EMPTY)
public record PortInfo(String id, String name, Direction direction, Provenance provenance, List<String> operations) {
}
