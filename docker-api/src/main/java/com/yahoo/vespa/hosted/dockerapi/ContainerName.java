// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Type-safe value wrapper for docker container names.
 *
 * @author bakksjo
 */
// TODO: Move this to node-admin when docker-api module can be removed
public class ContainerName {
    private static final Pattern LEGAL_CONTAINER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9-]+$");
    private final String name;

    public ContainerName(final String name) {
        this.name = Objects.requireNonNull(name);
        if (! LEGAL_CONTAINER_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Illegal container name: " + name + ". Must match " +
                    LEGAL_CONTAINER_NAME_PATTERN.toString());
        }
    }

    public String asString() {
        return name;
    }

    public static ContainerName fromHostname(final String hostName) {
        return new ContainerName(hostName.split("\\.", 2)[0]);
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
