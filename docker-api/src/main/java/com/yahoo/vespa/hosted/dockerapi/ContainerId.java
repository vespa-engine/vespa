// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Objects;

/**
 * The ID of a container.
 *
 * @author hakon
 */
public class ContainerId {
    private final String id;

    public ContainerId(String id) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerId that = (ContainerId) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
