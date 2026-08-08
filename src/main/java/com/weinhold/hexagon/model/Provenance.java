package com.weinhold.hexagon.model;

/**
 * Where the information about an element came from, so the UI can draw declared facts
 * solid and guessed ones dashed.
 */
public enum Provenance {
        /** Derived from an explicit annotation (jMolecules). */
        ANNOTATION,
        /** Inferred from a package or naming convention. */
        CONVENTION,
        /** Discovered by runtime inspection. */
        RUNTIME
}
