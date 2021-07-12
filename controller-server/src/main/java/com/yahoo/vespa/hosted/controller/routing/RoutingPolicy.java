// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents the DNS routing policy for a {@link com.yahoo.vespa.hosted.controller.application.Deployment}.
 *
 * @author mortent
 * @author mpolden
 */
public class RoutingPolicy {

    private final RoutingPolicyId id;
    private final HostName canonicalName;
    private final Optional<String> dnsZone;
    private final Set<EndpointId> endpoints;
    private final Status status;

    /** DO NOT USE. Public for serialization purposes */
    public RoutingPolicy(RoutingPolicyId id, HostName canonicalName, Optional<String> dnsZone, Set<EndpointId> endpoints,
                         Status status) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName must be non-null");
        this.dnsZone = Objects.requireNonNull(dnsZone, "dnsZone must be non-null");
        this.endpoints = ImmutableSortedSet.copyOf(Objects.requireNonNull(endpoints, "endpoints must be non-null"));
        this.status = Objects.requireNonNull(status, "status must be non-null");
    }

    /** The ID of this */
    public RoutingPolicyId id() {
        return id;
    }

    /** The canonical name for the load balancer this applies to (rhs of a CNAME or ALIAS record) */
    public HostName canonicalName() {
        return canonicalName;
    }

    /** DNS zone for the load balancer this applies to, if any. Used when creating ALIAS records. */
    public Optional<String> dnsZone() {
        return dnsZone;
    }

    /** The endpoints of this policy */
    public Set<EndpointId> endpoints() {
        return endpoints;
    }

    /** Returns the status of this */
    public Status status() {
        return status;
    }

    /** Returns a copy of this with status set to given status */
    public RoutingPolicy with(Status status) {
        return new RoutingPolicy(id, canonicalName, dnsZone, endpoints, status);
    }

    /** Returns the zone endpoints of this */
    public List<Endpoint> endpointsIn(SystemName system, RoutingMethod routingMethod, ZoneRegistry zoneRegistry) {
        Optional<Endpoint> infraEndpoint = SystemApplication.matching(id.owner())
                                                            .flatMap(app -> app.endpointIn(id.zone(), zoneRegistry));
        if (infraEndpoint.isPresent()) {
            return List.of(infraEndpoint.get());
        }
        List<Endpoint> endpoints = new ArrayList<>(3);
        endpoints.add(endpoint(routingMethod).target(id.cluster(), id.zone()).in(system));
        if (system.isPublic()) {
            endpoints.add(endpoint(routingMethod).target(id.cluster(), id.zone()).legacy().in(system));
        }
        // Add legacy endpoints
        if (routingMethod == RoutingMethod.shared) {
            endpoints.add(endpoint(routingMethod).target(id.cluster(), id.zone())
                                                 .on(Port.plain(4080))
                                                 .legacy()
                                                 .in(system));
            endpoints.add(endpoint(routingMethod).target(id.cluster(), id.zone())
                                                 .on(Port.tls(4443))
                                                 .legacy()
                                                 .in(system));
        }
        return endpoints;
    }

    /** Returns all region endpoints of this */
    public List<Endpoint> regionEndpointsIn(SystemName system, RoutingMethod routingMethod) {
        List<Endpoint> endpoints = new ArrayList<>(2);
        endpoints.add(regionEndpointIn(system, routingMethod, false));
        if (system.isPublic()) {
            endpoints.add(regionEndpointIn(system, routingMethod, true));
        }
        return Collections.unmodifiableList(endpoints);
    }

    /** Returns the region endpoint of this */
    public Endpoint regionEndpointIn(SystemName system, RoutingMethod routingMethod, boolean legacy) {
        Endpoint.EndpointBuilder endpoint = endpoint(routingMethod).targetRegion(id.cluster(), id.zone());
        if (legacy) {
            endpoint = endpoint.legacy();
        }
        return endpoint.in(system);
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
        return Text.format("%s [endpoints: %s%s], %s owned by %s, in %s", canonicalName, endpoints,
                             dnsZone.map(z -> ", DNS zone: " + z).orElse(""), id.cluster(), id.owner().toShortString(),
                             id.zone().value());
    }

    private Endpoint.EndpointBuilder endpoint(RoutingMethod routingMethod) {
        return Endpoint.of(id.owner())
                       .on(Port.fromRoutingMethod(routingMethod))
                       .routingMethod(routingMethod);
    }

}
