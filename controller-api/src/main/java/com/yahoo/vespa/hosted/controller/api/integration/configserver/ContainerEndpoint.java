// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.zone.AuthMethod;
import com.yahoo.config.provision.zone.RoutingMethod;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The endpoint of a container cluster. This encapsulates the endpoint details passed from controller to the config
 * server on deploy.
 *
 * @param clusterId     ID of the cluster to which this points
 * @param scope         Scope of this endpoint
 * @param names         All valid DNS names for this endpoint. This can contain both proper DNS names and synthetic identifiers
 *                      used for routing, such as a Host header value that is not necessarily a proper DNS name
 * @param weight        The relative weight of this endpoint
 * @param routingMethod The routing method used by this endpoint
 * @param authMethods   Supported authentication methods for each endpoint name
 *
 * @author mpolden
 */
public record ContainerEndpoint(String clusterId, String scope, List<String> names, OptionalInt weight,
                                RoutingMethod routingMethod, Map<String, AuthMethod> authMethods) {

    public ContainerEndpoint(String clusterId, String scope, List<String> names, OptionalInt weight,
                             RoutingMethod routingMethod, Map<String, AuthMethod> authMethods) {
        this.clusterId = nonEmpty(clusterId, "clusterId must be non-empty");
        this.scope = Objects.requireNonNull(scope, "scope must be non-null");
        this.names = List.copyOf(Objects.requireNonNull(names, "names must be non-null"));
        this.weight = Objects.requireNonNull(weight, "weight must be non-null");
        this.routingMethod = Objects.requireNonNull(routingMethod, "routingMethod must be non-null");
        this.authMethods = Objects.requireNonNull(Map.copyOf(authMethods), "authMethods must be non-null");
    }

    private static String nonEmpty(String s, String message) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(message);
        return s;
    }

}
