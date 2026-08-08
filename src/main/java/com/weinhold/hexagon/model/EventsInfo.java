package com.weinhold.hexagon.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Domain events grouped by direction. {@code published} events are those defined within the
 * service's own packages; {@code consumed} events require runtime listener inspection and
 * are not populated yet.
 */
@JsonInclude(NON_EMPTY)
public record EventsInfo(List<ComponentInfo> published, List<ComponentInfo> consumed) {
}
