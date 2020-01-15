// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.EndpointList;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents the DNS routing policy for a load balancer.
 *
 * @author mortent
 * @author mpolden
 */
public class RoutingPolicy {

    private final RoutingPolicyId id;
    private final HostName canonicalName;
    private final Optional<String> dnsZone;
    private final Set<EndpointId> endpoints;
    private final boolean loadBalancerActive;

    /** DO NOT USE. Public for serialization purposes */
    public RoutingPolicy(RoutingPolicyId id, HostName canonicalName,
                         Optional<String> dnsZone, Set<EndpointId> endpoints, boolean loadBalancerActive) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName must be non-null");
        this.dnsZone = Objects.requireNonNull(dnsZone, "dnsZone must be non-null");
        this.endpoints = ImmutableSortedSet.copyOf(Objects.requireNonNull(endpoints, "endpoints must be non-null"));
        this.loadBalancerActive = loadBalancerActive;
    }

    /** The ID of this */
    public RoutingPolicyId id() {
        return id;
    }

    /** The canonical name for this (rhs of a CNAME or ALIAS record) */
    public HostName canonicalName() {
        return canonicalName;
    }

    /** DNS zone for this, if any */
    public Optional<String> dnsZone() {
        return dnsZone;
    }

    /** The endpoints of this policy */
    public Set<EndpointId> endpoints() {
        return endpoints;
    }

    /** Returns whether the load balancer for this is active in node repository */
    public boolean loadBalancerActive() {
        return loadBalancerActive;
    }

    /** Returns the endpoint of this */
    public Endpoint endpointIn(SystemName system) {
        return Endpoint.of(id.owner()).target(id.cluster(), id.zone()).on(Port.tls()).directRouting().in(system);
    }

    /** Returns global endpoints which this is a member of */
    public EndpointList globalEndpointsIn(SystemName system) {
        return EndpointList.of(endpoints.stream().map(endpointId -> globalEndpointOf(id.owner(), endpointId, system)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingPolicy that = (RoutingPolicy) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s [rotations: %s%s], %s owned by %s, in %s", canonicalName, endpoints,
                             dnsZone.map(z -> ", DNS zone: " + z).orElse(""), id.cluster(), id.owner().toShortString(),
                             id.zone().value());
    }

    /** Creates a global endpoint for given application */
    public static Endpoint globalEndpointOf(ApplicationId application, EndpointId endpointId, SystemName system) {
        return Endpoint.of(application).named(endpointId).on(Port.tls()).directRouting().in(system);
    }

}
