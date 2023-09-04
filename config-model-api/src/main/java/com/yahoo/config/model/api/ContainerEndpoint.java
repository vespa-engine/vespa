// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * ContainerEndpoint tracks the service names that a Container Cluster should be
 * known as. This is used during request routing both for regular requests and
 * for health checks in traffic distribution.
 *
 * @author ogronnesby
 */
public class ContainerEndpoint {

    private final String clusterId;
    private final ApplicationClusterEndpoint.Scope scope;
    private final List<String> names;
    private final OptionalInt weight;
    private final ApplicationClusterEndpoint.RoutingMethod routingMethod;

    public ContainerEndpoint(String clusterId, ApplicationClusterEndpoint.Scope scope, List<String> names) {
        this(clusterId, scope, names, OptionalInt.empty());
    }

    public ContainerEndpoint(String clusterId, ApplicationClusterEndpoint.Scope scope, List<String> names, OptionalInt weight) {
        this(clusterId, scope, names, weight, ApplicationClusterEndpoint.RoutingMethod.sharedLayer4);
    }

    public ContainerEndpoint(String clusterId, ApplicationClusterEndpoint.Scope scope, List<String> names, OptionalInt weight, ApplicationClusterEndpoint.RoutingMethod routingMethod) {
        this.clusterId = Objects.requireNonNull(clusterId);
        this.scope = Objects.requireNonNull(scope);
        this.names = List.copyOf(Objects.requireNonNull(names));
        this.weight = weight;
        this.routingMethod = routingMethod;
    }

    public String clusterId() {
        return clusterId;
    }

    public List<String> names() {
        return names;
    }

    public ApplicationClusterEndpoint.Scope scope() {
        return scope;
    }

    public OptionalInt weight() {
        return weight;
    }

    public ApplicationClusterEndpoint.RoutingMethod routingMethod() {
        return routingMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerEndpoint that = (ContainerEndpoint) o;
        return Objects.equals(clusterId, that.clusterId) &&
               Objects.equals(scope, that.scope) &&
               Objects.equals(names, that.names) &&
               Objects.equals(weight, that.weight) &&
               Objects.equals(routingMethod, that.routingMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, names, scope, weight, routingMethod);
    }

    @Override
    public String toString() {
        return String.format("container endpoint %s -> %s [scope=%s, weight=%s, routingMetod=%s]", clusterId, names, scope, weight, routingMethod);
    }
}
