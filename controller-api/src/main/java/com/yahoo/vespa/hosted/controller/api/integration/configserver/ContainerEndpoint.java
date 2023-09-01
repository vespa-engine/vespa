// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.zone.AuthMethod;
import com.yahoo.config.provision.zone.RoutingMethod;

import java.util.List;
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
 * @param dnsNames      All valid DNS names for this endpoint, and properties unique to each name. This is guaranteed
 *                      to contain the same names as the names parameter
 *
 * @author mpolden
 */
// TODO(mpolden): Remove 'names'
public record ContainerEndpoint(String clusterId, String scope, List<String> names, OptionalInt weight,
                                RoutingMethod routingMethod, List<DnsName> dnsNames) {

    public ContainerEndpoint(String clusterId, String scope, List<String> names, OptionalInt weight,
                             RoutingMethod routingMethod, List<DnsName> dnsNames) {
        this.clusterId = nonEmpty(clusterId, "clusterId must be non-empty");
        this.scope = Objects.requireNonNull(scope, "scope must be non-null");
        this.names = List.copyOf(Objects.requireNonNull(names, "names must be non-null"));
        this.weight = Objects.requireNonNull(weight, "weight must be non-null");
        this.routingMethod = Objects.requireNonNull(routingMethod, "routingMethod must be non-null");
        this.dnsNames = List.copyOf(Objects.requireNonNull(dnsNames, "dnsNames must be non-null"));
        List<String> otherNames = dnsNames.stream().map(DnsName::dnsName).toList();
        if (!names.equals(otherNames)) {
            throw new IllegalArgumentException("dnsNames differ from names, got names=" + names +
                                               " and dnsNames=" + otherNames);
        }
    }

    /** A DNS name for this endpoint, and properties unique to this particular name */
    public record DnsName(String dnsName, AuthMethod authMethod) {}

    private static String nonEmpty(String s, String message) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(message);
        return s;
    }

}
