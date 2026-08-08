package com.weinhold.hexagon.sample;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter(name = "Order REST API")
public class OrderController implements PlaceOrderUseCase {

	@Override
	public void placeOrder() {
	}

}
