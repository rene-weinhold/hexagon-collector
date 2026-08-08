package com.weinhold.hexagon.model;

/**
 * Whether a port or adapter is driving the application or driven by it.
 */
public enum Direction {
        /** Driving side — inbound. jMolecules {@code @PrimaryPort} / {@code @PrimaryAdapter}. */
        PRIMARY,
        /** Driven side — outbound. jMolecules {@code @SecondaryPort} / {@code @SecondaryAdapter}. */
        SECONDARY
}
