package com.weinhold.hexagon.external;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * An event owned by another service, of the kind a shared contract library would carry. It
 * deliberately lives outside every scanned base package: a consumed event is by definition
 * somebody else's published event, so it can never be found by scanning our own packages.
 */
@DomainEvent(name = "ShipmentDispatched")
public class ShipmentDispatched {

}
