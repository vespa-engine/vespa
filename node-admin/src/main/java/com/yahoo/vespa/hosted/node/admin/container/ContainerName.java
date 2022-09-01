// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Type-safe value wrapper for docker container names.
 *
 * @author bakksjo
 */
public class ContainerName implements Comparable<ContainerName> {

    private static final Pattern LEGAL_CONTAINER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private final String name;

    public ContainerName(final String name) {
        this.name = Objects.requireNonNull(name);
        if (! LEGAL_CONTAINER_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Illegal container name: " + name + ". Must match " +
                    LEGAL_CONTAINER_NAME_PATTERN.pattern());
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
    public boolean equals(Object o) {
        if (!(o instanceof ContainerName other)) {
            return false;
        }

        return Objects.equals(name, other.name);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {"
                + " name=" + name
                + " }";
    }

    @Override
    public int compareTo(ContainerName o) {
        return name.compareTo(o.name);
    }

}
