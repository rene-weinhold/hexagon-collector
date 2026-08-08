package com.weinhold.hexagon.conventions.application.port.out;

import com.weinhold.hexagon.conventions.application.Readable;

public interface OrderRepository extends Readable {

    void save();

}
