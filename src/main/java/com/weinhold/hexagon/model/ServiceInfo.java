package com.weinhold.hexagon.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Identity of the service being described — the "who am I" block of the contract.
 * Only {@code id} is meaningful for the landscape; everything else is optional.
 */
@JsonInclude(NON_EMPTY)
public record ServiceInfo(String id, String displayName, String version, String environment, String instanceId,
        String basePackage, String repository) {
}
