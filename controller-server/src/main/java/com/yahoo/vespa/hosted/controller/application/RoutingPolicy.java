// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents the DNS routing policy for a load balancer. A routing policy is uniquely identified by its owner, cluster
 * and zone.
 *
 * @author mortent
 * @author mpolden
 */
public class RoutingPolicy {

    private final ApplicationId owner;
    private final ClusterSpec.Id cluster;
    private final ZoneId zone;
    private final HostName canonicalName;
    private final Optional<String> dnsZone;
    private final Set<EndpointId> endpoints;
    private final boolean active;

    /** DO NOT USE. Public for serialization purposes */
    public RoutingPolicy(ApplicationId owner, ClusterSpec.Id cluster, ZoneId zone, HostName canonicalName,
                         Optional<String> dnsZone, Set<EndpointId> endpoints, boolean active) {
        this.owner = Objects.requireNonNull(owner, "owner must be non-null");
        this.cluster = Objects.requireNonNull(cluster, "cluster must be non-null");
        this.zone = Objects.requireNonNull(zone, "zone must be non-null");
        this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName must be non-null");
        this.dnsZone = Objects.requireNonNull(dnsZone, "dnsZone must be non-null");
        this.endpoints = ImmutableSortedSet.copyOf(Objects.requireNonNull(endpoints, "endpoints must be non-null"));
        this.active = active;
    }

    /** The application owning this */
    public ApplicationId owner() {
        return owner;
    }

    /** The zone this applies to */
    public ZoneId zone() {
        return zone;
    }

    /** The cluster this applies to */
    public ClusterSpec.Id cluster() {
        return cluster;
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

    /** Returns whether this is active (the underlying load balancer is in an active state) */
    public boolean active() {
        return active;
    }

    /** Returns the endpoint of this */
    public Endpoint endpointIn(SystemName system) {
        return Endpoint.of(owner).target(cluster, zone).on(Port.tls()).directRouting().in(system);
    }

    /** Returns rotation endpoints of this */
    public EndpointList rotationEndpointsIn(SystemName system) {
        return EndpointList.of(endpoints.stream().map(endpointId -> endpointOf(owner, endpointId, system)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingPolicy that = (RoutingPolicy) o;
        return owner.equals(that.owner) &&
               cluster.equals(that.cluster) &&
               zone.equals(that.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, cluster, zone);
    }

    @Override
    public String toString() {
        return String.format("%s [rotations: %s%s], %s owned by %s, in %s", canonicalName, endpoints,
                             dnsZone.map(z -> ", DNS zone: " + z).orElse(""), cluster, owner.toShortString(),
                             zone.value());
    }

    /** Returns the endpoint of given rotation */
    public static Endpoint endpointOf(ApplicationId application, EndpointId endpointId, SystemName system) {
        return Endpoint.of(application).named(endpointId).on(Port.tls()).directRouting().in(system);
    }

}
