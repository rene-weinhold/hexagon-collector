package com.weinhold.hexagon.model;

/**
 * How sure the starter is about a contact point's canonical key. HIGH means it was read
 * from framework metadata or a declarative annotation; MEDIUM means it was partially
 * inferred (e.g. an unresolved property placeholder).
 */
public enum Confidence {

        HIGH, MEDIUM, LOW;

    /**
     * One step less certain, bottoming out at {@link #LOW}. Detectors call this for every
     * piece of the key they had to guess — an unresolved placeholder, a class-name
     * convention, a missing target — so that a key assembled from several guesses cannot
     * come out looking like a fact.
     */
    public Confidence downgrade() {
        return this == LOW ? LOW : values()[ordinal() + 1];
    }
}
