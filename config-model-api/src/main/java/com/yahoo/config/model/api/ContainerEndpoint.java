// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.List;
import java.util.Objects;

/**
 * ContainerEndpoint tracks the service names that a Container Cluster should be
 * known as. This is used during request routing both for regular requests and
 * for health checks in traffic distribution.
 *
 * @author ogronnesby
 */
public class ContainerEndpoint {

    private final String clusterId;
    private final List<String> names;

    public ContainerEndpoint(String clusterId, List<String> names) {
        this.clusterId = Objects.requireNonNull(clusterId);
        this.names = List.copyOf(Objects.requireNonNull(names));
    }

    public String clusterId() {
        return clusterId;
    }

    public List<String> names() {
        return names;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerEndpoint that = (ContainerEndpoint) o;
        return Objects.equals(clusterId, that.clusterId) &&
                Objects.equals(names, that.names);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, names);
    }

    @Override
    public String toString() {
        return String.format("container endpoint %s -> %s", clusterId, names);
    }

}
