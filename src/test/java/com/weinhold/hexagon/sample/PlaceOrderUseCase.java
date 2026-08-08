package com.weinhold.hexagon.sample;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort(name = "PlaceOrder")
public interface PlaceOrderUseCase {

	void placeOrder();

}
