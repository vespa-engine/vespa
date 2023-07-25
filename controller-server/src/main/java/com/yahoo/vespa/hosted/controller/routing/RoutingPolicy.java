// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import ai.vespa.http.DomainName;
import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.GeneratedEndpoint;

import java.util.ArrayList;
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
                            RoutingStatus routingStatus,
                            boolean isPublic,
                            List<GeneratedEndpoint> generatedEndpoints) {

    /** DO NOT USE. Public for serialization purposes */
    public RoutingPolicy(RoutingPolicyId id, Optional<DomainName> canonicalName, Optional<String> ipAddress, Optional<String> dnsZone,
                         Set<EndpointId> instanceEndpoints, Set<EndpointId> applicationEndpoints, RoutingStatus routingStatus, boolean isPublic,
                         List<GeneratedEndpoint> generatedEndpoints) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName must be non-null");
        this.ipAddress = Objects.requireNonNull(ipAddress, "ipAddress must be non-null");
        this.dnsZone = Objects.requireNonNull(dnsZone, "dnsZone must be non-null");
        this.instanceEndpoints = ImmutableSortedSet.copyOf(Objects.requireNonNull(instanceEndpoints, "instanceEndpoints must be non-null"));
        this.applicationEndpoints = ImmutableSortedSet.copyOf(Objects.requireNonNull(applicationEndpoints, "applicationEndpoints must be non-null"));
        this.routingStatus = Objects.requireNonNull(routingStatus, "status must be non-null");
        this.isPublic = isPublic;
        this.generatedEndpoints = List.copyOf(Objects.requireNonNull(generatedEndpoints, "generatedEndpoints must be non-null"));

        if (canonicalName.isEmpty() == ipAddress.isEmpty())
            throw new IllegalArgumentException("Exactly 1 of canonicalName=%s and ipAddress=%s must be set".formatted(
                    canonicalName.map(DomainName::value).orElse("<empty>"), ipAddress.orElse("<empty>")));
        if ( ! instanceEndpoints.isEmpty() && ! isPublic)
            throw new IllegalArgumentException("Non-public zone endpoint cannot be part of any global endpoint, but was in: " + instanceEndpoints);
        if ( ! applicationEndpoints.isEmpty() && ! isPublic)
            throw new IllegalArgumentException("Non-public zone endpoint cannot be part of any application endpoint, but was in: " + applicationEndpoints);
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

    /** The application-level endpoints this participates in */
    public Set<EndpointId> applicationEndpoints() {
        return applicationEndpoints;
    }

    /** The endpoints to generate for this policy, if any */
    public List<GeneratedEndpoint> generatedEndpoints() {
        return generatedEndpoints;
    }

    /** Return status of routing */
    public RoutingStatus routingStatus() {
        return routingStatus;
    }

    /** Returns whether this has a load balancer which is available from public internet. */
    public boolean isPublic() {
        return isPublic;
    }

    /** Returns whether this policy applies to given deployment */
    public boolean appliesTo(DeploymentId deployment) {
        return id.owner().equals(deployment.applicationId()) &&
               id.zone().equals(deployment.zoneId());
    }

    /** Returns a copy of this with routing status set to given status */
    public RoutingPolicy with(RoutingStatus routingStatus) {
        return new RoutingPolicy(id, canonicalName, ipAddress, dnsZone, instanceEndpoints, applicationEndpoints, routingStatus, isPublic, generatedEndpoints);
    }

    public RoutingPolicy with(List<GeneratedEndpoint> generatedEndpoints) {
        return new RoutingPolicy(id, canonicalName, ipAddress, dnsZone, instanceEndpoints, applicationEndpoints, routingStatus, isPublic, generatedEndpoints);
    }

    /** Returns the zone endpoints of this */
    public List<Endpoint> zoneEndpointsIn(SystemName system, RoutingMethod routingMethod, boolean includeTokenEndpoint) {
        DeploymentId deployment = new DeploymentId(id.owner(), id.zone());
        Endpoint.EndpointBuilder builder = endpoint(routingMethod).target(id.cluster(), deployment);
        Endpoint zoneEndpoint = builder.in(system);
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(zoneEndpoint);
        if (includeTokenEndpoint) {
            Endpoint tokenEndpoint = builder.authMethod(Endpoint.AuthMethod.token).in(system);
            endpoints.add(tokenEndpoint);
        }
        for (var generatedEndpoint : generatedEndpoints) {
            boolean include = switch (generatedEndpoint.authMethod()) {
                case token -> includeTokenEndpoint;
                case mtls -> true;
            };
            if (include) {
                endpoints.add(builder.generatedFrom(generatedEndpoint).in(system));
            }
        }
        return endpoints;
    }

    /** Returns the region endpoint of this */
    public Endpoint regionEndpointIn(SystemName system, RoutingMethod routingMethod, Optional<GeneratedEndpoint> generated) {
        Endpoint.EndpointBuilder builder = endpoint(routingMethod).targetRegion(id.cluster(), id.zone());
        generated.ifPresent(builder::generatedFrom);
        return builder.in(system);
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

    private Endpoint.EndpointBuilder endpoint(RoutingMethod routingMethod) {
        return Endpoint.of(id.owner())
                       .on(Port.fromRoutingMethod(routingMethod))
                       .routingMethod(routingMethod);
    }

}
