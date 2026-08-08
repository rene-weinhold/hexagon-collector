package com.weinhold.hexagon.model;

/**
 * Transport protocol of a {@link ContactPointInfo}. Drives the canonical key format.
 */
public enum Protocol {
        HTTP, KAFKA, AMQP, JDBC, GRPC, FILE, OTHER
}
