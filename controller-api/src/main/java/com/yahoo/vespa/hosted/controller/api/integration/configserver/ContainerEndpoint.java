// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.zone.RoutingMethod;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * This represents a list of one or more names for a container cluster.
 *
 * @author mpolden
 */
public class ContainerEndpoint {

    private final String clusterId;
    private final String scope;
    private final List<String> names;
    private final OptionalInt weight;
    private final RoutingMethod routingMethod;

    public ContainerEndpoint(String clusterId, String scope, List<String> names, OptionalInt weight, RoutingMethod routingMethod) {
        this.clusterId = nonEmpty(clusterId, "clusterId must be non-empty");
        this.scope = Objects.requireNonNull(scope, "scope must be non-null");
        this.names = List.copyOf(Objects.requireNonNull(names, "names must be non-null"));
        this.weight = Objects.requireNonNull(weight, "weight must be non-null");
        this.routingMethod = Objects.requireNonNull(routingMethod, "routingMethod must be non-null");
    }

    /** ID of the cluster to which this points */
    public String clusterId() {
        return clusterId;
    }

    /** The scope of this endpoint */
    public String scope() {
        return scope;
    }

    /**
     * All valid DNS names for this endpoint. This can contain both proper DNS names and synthetic identifiers used for
     * routing, such as a Host header value that is not necessarily a proper DNS name.
     */
    public List<String> names() {
        return names;
    }

    /** The relative weight of this endpoint */
    public OptionalInt weight() {
        return weight;
    }

    /** The routing method used by this endpoint */
    public RoutingMethod routingMethod() {
        return routingMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerEndpoint that = (ContainerEndpoint) o;
        return clusterId.equals(that.clusterId) && scope.equals(that.scope) && names.equals(that.names) && weight.equals(that.weight) && routingMethod == that.routingMethod;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, scope, names, weight, routingMethod);
    }

    @Override
    public String toString() {
        return "container endpoint for cluster " + clusterId + ": " + String.join(", ", names) +
               " [method=" + routingMethod + ",scope=" + scope + ",weight=" +
               weight.stream().boxed().map(Object::toString).findFirst().orElse("<none>") + "]";
    }

    private static String nonEmpty(String s, String message) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(message);
        return s;
    }

}
