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
    private final ZoneId zone;
    private final HostName alias;
    private final HostName canonicalName;
    private final Optional<String> dnsZone;
    private final Set<RotationName> rotations;

    /** DO NOT USE. Public for serialization purposes */
    public RoutingPolicy(ApplicationId owner, ZoneId zone, HostName alias, HostName canonicalName,
                         Optional<String> dnsZone, Set<RotationName> rotations) {
        this.owner = Objects.requireNonNull(owner, "owner must be non-null");
        this.zone = Objects.requireNonNull(zone, "zone must be non-null");
        this.alias = Objects.requireNonNull(alias, "alias must be non-null");
        this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName must be non-null");
        this.dnsZone = Objects.requireNonNull(dnsZone, "dnsZone must be non-null");
        this.rotations = ImmutableSortedSet.copyOf(Objects.requireNonNull(rotations, "rotations must be non-null"));
    }

    public RoutingPolicy(ApplicationId owner, ZoneId zone, ClusterSpec.Id cluster, SystemName system, HostName canonicalName,
                         Optional<String> dnsZone, Set<RotationName> rotations) {
        this(owner, zone, HostName.from(endpointOf(cluster, owner, zone, system).dnsName()), canonicalName, dnsZone, rotations);
    }

    /** The application owning this */
    public ApplicationId owner() {
        return owner;
    }

    /** The zone this applies to */
    public ZoneId zone() {
        return zone;
    }

    /** This alias (lhs of a CNAME or ALIAS record) */
    public HostName alias() {
        return alias;
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

    /** Endpoints for this routing policy */
    public EndpointList endpointsIn(SystemName system) {
        return EndpointList.of(rotations.stream().map(rotation -> endpointOf(owner, rotation, system)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingPolicy policy = (RoutingPolicy) o;
        return owner.equals(policy.owner) &&
               zone.equals(policy.zone) &&
               canonicalName.equals(policy.canonicalName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, zone, canonicalName);
    }

    @Override
    public String toString() {
        return String.format("%s -> %s [rotations: %s%s], owned by %s, in %s", alias, canonicalName, rotations,
                             dnsZone.map(z -> ", DNS zone: " + z).orElse(""), owner.toShortString(),
                             zone.value());
    }

    /** Returns the endpoint of given rotation */
    public static Endpoint endpointOf(ApplicationId application, RotationName rotation, SystemName system) {
        return Endpoint.of(application).target(rotation).on(Port.tls()).directRouting().in(system);
    }

    /** Returns the endpoint of given cluster */
    public static Endpoint endpointOf(ClusterSpec.Id cluster, ApplicationId application, ZoneId zone, SystemName system) {
        return Endpoint.of(application).target(cluster, zone).on(Port.tls()).directRouting().in(system);
    }

}
