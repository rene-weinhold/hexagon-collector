package com.weinhold.hexagon.conventions.application;

/**
 * A super-interface of a port, deliberately outside the {@code port} package so it is not a
 * port itself. It exists so the scanner can be held to reporting inherited operations: those
 * are part of the hole in the hexagon just as much as the declared ones.
 */
public interface Readable {

	void findById();

}
