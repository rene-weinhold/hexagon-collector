package com.weinhold.hexagon.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An adapter — what plugs into a port. {@code id} (FQCN) and {@code direction} are
 * required. An empty {@code implementsPorts} on an adapter that should have one is an
 * architecture smell the UI can flag. {@code contactPoints} are the adapter's touchpoints
 * with the outside world.
 */
@JsonInclude(NON_EMPTY)
public record AdapterInfo(String id, String name, Direction direction, String technology, Provenance provenance,
        List<String> implementsPorts, List<ContactPointInfo> contactPoints) {
}
