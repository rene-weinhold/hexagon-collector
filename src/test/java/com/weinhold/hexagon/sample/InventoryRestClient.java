package com.weinhold.hexagon.sample;

import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@SecondaryAdapter
public class InventoryRestClient implements InventoryPort {

    @Override
    public void reserveStock() {
    }

    @Override
    public void releaseStock() {
    }

}
