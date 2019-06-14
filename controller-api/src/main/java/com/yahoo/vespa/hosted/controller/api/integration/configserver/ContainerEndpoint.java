// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import java.util.List;
import java.util.Objects;

/**
 * This represents a list of one or more names for a container cluster.
 *
 * @author mpolden
 */
public class ContainerEndpoint {

    private final String clusterId;
    private final List<String> names;

    public ContainerEndpoint(String clusterId, List<String> names) {
        this.clusterId = nonEmpty(clusterId, "message must be non-empty");
        this.names = List.copyOf(Objects.requireNonNull(names, "names must be non-null"));
    }

    /** ID of the cluster to which this points */
    public String clusterId() {
        return clusterId;
    }

    /**
     * All valid DNS names for this endpoint. This can contain both proper DNS names and synthetic identifiers used for
     * routing, such as a Host header value that is not necessarily a proper DNS name.
     */
    public List<String> names() {
        return names;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerEndpoint that = (ContainerEndpoint) o;
        return clusterId.equals(that.clusterId) &&
               names.equals(that.names);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, names);
    }

    @Override
    public String toString() {
        return "container endpoint for " + clusterId + " " + names;
    }

    private static String nonEmpty(String s, String message) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(message);
        return s;
    }

}
