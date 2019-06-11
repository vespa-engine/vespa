// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.vespa.applicationmodel.ClusterId;

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

    private final ClusterId clusterId;
    private final List<String> names;

    public ContainerEndpoint(ClusterId clusterId, List<String> names) {
        this.clusterId = Objects.requireNonNull(clusterId);
        this.names = List.copyOf(Objects.requireNonNull(names));
    }

    public ClusterId clusterId() {
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
        return "ContainerEndpoint{" +
                "clusterId=" + clusterId +
                ", names=" + names +
                '}';
    }

}
