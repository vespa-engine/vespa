// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;


import java.util.Objects;

/**
 * A Brooklyn rotation, e.g. rotation-042.vespa.a02.yahoodns.net.
 */
public class Rotation {

    private final String id;

    public Rotation(String id) {
        this.id = Objects.requireNonNull(id, "Rotation id cannot be null");
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rotation)) {
            return false;
        }
        final Rotation that = (Rotation) o;
        return (this.id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
