package com.weinhold.hexagon.listener;

import org.springframework.context.event.EventListener;

import com.weinhold.hexagon.external.ShipmentDispatched;

public class ShipmentEventListener {

	@EventListener
	public void on(ShipmentDispatched event) {
	}

	@EventListener
	public void ignoresNonEvents(String notAnEvent) {
	}

}
