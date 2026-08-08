package com.weinhold.hexagon.sample;

import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface InventoryPort {

	void reserveStock();

	void releaseStock();

}
