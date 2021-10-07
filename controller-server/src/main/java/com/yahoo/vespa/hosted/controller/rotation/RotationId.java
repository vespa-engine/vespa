// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.rotation;

import java.util.Objects;

/**
 * ID of a global rotation.
 *
 * @author mpolden
 */
public class RotationId {

    private final String id;

    public RotationId(String id) {
        this.id = id;
    }

    /** Rotation ID, e.g. rotation-42.vespa.global.routing */
    public String asString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RotationId that = (RotationId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "rotation ID " + id;
    }

}
