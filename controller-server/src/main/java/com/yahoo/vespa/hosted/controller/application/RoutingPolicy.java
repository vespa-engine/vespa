// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;

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

    private final ApplicationId owner;
    private final ClusterSpec.Id cluster;
    private final ZoneId zone;
    private final HostName canonicalName;
    private final Optional<String> dnsZone;
    private final Set<RotationName> rotations;

    /** DO NOT USE. Public for serialization purposes */
    public RoutingPolicy(ApplicationId owner, ClusterSpec.Id cluster, ZoneId zone, HostName canonicalName,
                         Optional<String> dnsZone, Set<RotationName> rotations) {
        this.owner = Objects.requireNonNull(owner, "owner must be non-null");
        this.cluster = Objects.requireNonNull(cluster, "cluster must be non-null");
        this.zone = Objects.requireNonNull(zone, "zone must be non-null");
        this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName must be non-null");
        this.dnsZone = Objects.requireNonNull(dnsZone, "dnsZone must be non-null");
        this.rotations = ImmutableSortedSet.copyOf(Objects.requireNonNull(rotations, "rotations must be non-null"));
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

    /** The rotations in this policy */
    public Set<RotationName> rotations() {
        return rotations;
    }

    /** Returns the endpoint of this */
    public Endpoint endpointIn(SystemName system) {
        return Endpoint.of(owner).target(cluster, zone).on(Port.tls()).directRouting().in(system);
    }

    /** Returns rotation endpoints of this */
    public EndpointList rotationEndpointsIn(SystemName system) {
        return EndpointList.of(rotations.stream().map(rotation -> endpointOf(owner, rotation, system)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingPolicy policy = (RoutingPolicy) o;
        return owner.equals(policy.owner) &&
               cluster.equals(policy.cluster) &&
               zone.equals(policy.zone) &&
               canonicalName.equals(policy.canonicalName) &&
               dnsZone.equals(policy.dnsZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, cluster, zone, canonicalName, dnsZone);
    }

    @Override
    public String toString() {
        return String.format("%s [rotations: %s%s], %s owned by %s, in %s", canonicalName, rotations,
                             dnsZone.map(z -> ", DNS zone: " + z).orElse(""), cluster, owner.toShortString(),
                             zone.value());
    }

    /** Returns the endpoint of given rotation */
    public static Endpoint endpointOf(ApplicationId application, RotationName rotation, SystemName system) {
        return Endpoint.of(application).target(rotation).on(Port.tls()).directRouting().in(system);
    }

}
