// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import ai.vespa.http.DomainName;
import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import com.yahoo.vespa.hosted.controller.application.EndpointId;

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
public record RoutingPolicy(RoutingPolicyId id,
                            Optional<DomainName> canonicalName,
                            Optional<String> ipAddress,
                            Optional<String> dnsZone,
                            Set<EndpointId> instanceEndpoints,
                            Set<EndpointId> applicationEndpoints,
                            Status status) {

    /** DO NOT USE. Public for serialization purposes */
    public RoutingPolicy(RoutingPolicyId id, Optional<DomainName> canonicalName, Optional<String> ipAddress, Optional<String> dnsZone,
                         Set<EndpointId> instanceEndpoints, Set<EndpointId> applicationEndpoints, Status status) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName must be non-null");
        this.ipAddress = Objects.requireNonNull(ipAddress, "ipAddress must be non-null");
        this.dnsZone = Objects.requireNonNull(dnsZone, "dnsZone must be non-null");
        this.instanceEndpoints = ImmutableSortedSet.copyOf(Objects.requireNonNull(instanceEndpoints, "instanceEndpoints must be non-null"));
        this.applicationEndpoints = ImmutableSortedSet.copyOf(Objects.requireNonNull(applicationEndpoints, "applicationEndpoints must be non-null"));
        this.status = Objects.requireNonNull(status, "status must be non-null");

        if (canonicalName.isEmpty() == ipAddress.isEmpty())
            throw new IllegalArgumentException("Exactly 1 of canonicalName=%s and ipAddress=%s must be set".formatted(
                    canonicalName.map(DomainName::value).orElse("<empty>"), ipAddress.orElse("<empty>")));
    }

    /** The ID of this */
    public RoutingPolicyId id() {
        return id;
    }

    /** The canonical name for the load balancer this applies to (rhs of a CNAME or ALIAS record) */
    public Optional<DomainName> canonicalName() {
        return canonicalName;
    }

    /** The IP address for the load balancer this applies to (rhs of an A or DIRECT record) */
    public Optional<String> ipAddress() {
        return ipAddress;
    }

    /** DNS zone for the load balancer this applies to, if any. Used when creating ALIAS records. */
    public Optional<String> dnsZone() {
        return dnsZone;
    }

    /** The instance-level endpoints this participates in */
    public Set<EndpointId> instanceEndpoints() {
        return instanceEndpoints;
    }

    /** The application-level endpoints  this participates in */
    public Set<EndpointId> applicationEndpoints() {
        return applicationEndpoints;
    }

    /** Returns the status of this */
    public Status status() {
        return status;
    }

    /** Returns whether this policy applies to given deployment */
    public boolean appliesTo(DeploymentId deployment) {
        return id.owner().equals(deployment.applicationId()) &&
               id.zone().equals(deployment.zoneId());
    }

    /** Returns a copy of this with status set to given status */
    public RoutingPolicy with(Status status) {
        return new RoutingPolicy(id, canonicalName, ipAddress, dnsZone, instanceEndpoints, applicationEndpoints, status);
    }

    /** Returns the zone endpoints of this */
    public List<Endpoint> zoneEndpointsIn(SystemName system, RoutingMethod routingMethod, ZoneRegistry zoneRegistry) {
        DeploymentId deployment = new DeploymentId(id.owner(), id.zone());
        return List.of(endpoint(routingMethod, zoneRegistry).target(id.cluster(), deployment).in(system));
    }

    /** Returns the region endpoint of this */
    public Endpoint regionEndpointIn(SystemName system, RoutingMethod routingMethod, ZoneRegistry zoneRegistry) {
        return endpoint(routingMethod, zoneRegistry).targetRegion(id.cluster(), id.zone()).in(system);
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
        return Text.format("%s [instance endpoints: %s, application endpoints: %s%s], %s owned by %s, in %s", canonicalName,
                           instanceEndpoints, applicationEndpoints,
                           dnsZone.map(z -> ", DNS zone: " + z).orElse(""), id.cluster(), id.owner().toShortString(),
                           id.zone().value());
    }

    private Endpoint.EndpointBuilder endpoint(RoutingMethod routingMethod, ZoneRegistry zones) {
        return Endpoint.of(id.owner())
                       .on(Port.fromRoutingMethod(routingMethod))
                       .routingMethod(routingMethod);
    }

    /** The status of a routing policy */
    public record Status(boolean active, RoutingStatus routingStatus) {

        /** DO NOT USE. Public for serialization purposes */
        public Status {
            Objects.requireNonNull(routingStatus, "routingStatus must be non-null");
        }

        /** Returns whether this is considered active according to the load balancer status */
        public boolean isActive() {
            return active;
        }

        /** Return status of routing */
        public RoutingStatus routingStatus() {
            return routingStatus;
        }

        /** Returns a copy of this with routing status changed */
        public Status with(RoutingStatus routingStatus) {
            return new Status(active, routingStatus);
        }

    }

}
