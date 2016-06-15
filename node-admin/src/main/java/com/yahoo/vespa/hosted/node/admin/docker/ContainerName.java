// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import java.util.Objects;

/**
 * Type-safe value wrapper for docker container names.
 *
 * @author bakksjo
 */
public class ContainerName {
    private final String name;

    public ContainerName(final String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String asString() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof ContainerName)) {
            return false;
        }

        final ContainerName other = (ContainerName) o;

        return Objects.equals(name, other.name);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {"
                + " name=" + name
                + " }";
    }
}
