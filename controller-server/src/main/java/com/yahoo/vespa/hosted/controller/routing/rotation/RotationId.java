// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing.rotation;

/**
 * ID of a global rotation.
 *
 * @author mpolden
 */
public record RotationId(String id) {

    /** Rotation ID, e.g. rotation-42.vespa.global.routing */
    public String asString() {
        return id;
    }

    @Override
    public String toString() {
        return "rotation ID " + id;
    }

}
