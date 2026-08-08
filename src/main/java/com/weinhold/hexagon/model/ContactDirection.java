package com.weinhold.hexagon.model;

/**
 * Direction of a contact point relative to this service. Named distinctly from
 * {@link Direction} (which is about ports/adapters being primary/secondary).
 */
public enum ContactDirection {
        /** The service receives on this contact point (e.g. an HTTP route it serves). */
        INBOUND,
        /** The service reaches out on this contact point (e.g. a database it queries). */
        OUTBOUND
}
