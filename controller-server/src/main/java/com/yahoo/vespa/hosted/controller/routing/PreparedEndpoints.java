package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.EndpointList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This represents the endpoints, and associated resources, that have been prepared for a deployment.
 *
 * @author mpolden
 */
public record PreparedEndpoints(DeploymentId deployment,
                                EndpointList endpoints,
                                List<AssignedRotation> rotations,
                                Optional<EndpointCertificate> certificate) {

    public PreparedEndpoints(DeploymentId deployment, EndpointList endpoints, List<AssignedRotation> rotations, Optional<EndpointCertificate> certificate) {
        this.deployment = Objects.requireNonNull(deployment);
        this.endpoints = Objects.requireNonNull(endpoints);
        this.rotations = List.copyOf(Objects.requireNonNull(rotations));
        this.certificate = Objects.requireNonNull(certificate);
        certificate.ifPresent(endpointCertificate -> requireMatchingSans(endpointCertificate, endpoints));
    }

    /** Returns the endpoints contained in this as {@link com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint} */
    public Set<ContainerEndpoint> containerEndpoints() {
        Map<EndpointId, AssignedRotation> rotationsByEndpointId = rotations.stream()
                                                                           .collect(Collectors.toMap(AssignedRotation::endpointId,
                                                                                                     Function.identity()));
        Set<ContainerEndpoint> containerEndpoints = new HashSet<>();
        endpoints.scope(Endpoint.Scope.zone).groupingBy(Endpoint::cluster).forEach((clusterId, clusterEndpoints) -> {
            clusterEndpoints.groupingBy(Endpoint::authMethod).forEach((authMethod, endpointsByAuthMethod) -> {
                containerEndpoints.add(new ContainerEndpoint(clusterId.value(),
                                                             asString(Endpoint.Scope.zone),
                                                             endpointsByAuthMethod.mapToList(Endpoint::dnsName),
                                                             OptionalInt.empty(),
                                                             endpointsByAuthMethod.first().get().routingMethod(),
                                                             authMethod));
            });
        });
        endpoints.scope(Endpoint.Scope.global).groupingBy(Endpoint::cluster).forEach((clusterId, clusterEndpoints) -> {
            for (var endpoint : clusterEndpoints) {
                List<String> names = new ArrayList<>(2);
                names.add(endpoint.dnsName());
                if (endpoint.requiresRotation()) {
                    EndpointId endpointId = EndpointId.of(endpoint.name());
                    AssignedRotation rotation = rotationsByEndpointId.get(endpointId);
                    if (rotation == null) {
                        throw new IllegalStateException(endpoint + " requires a rotation, but no rotation has been assigned to " + endpointId);
                    }
                    // Include the rotation ID as a valid name of this container endpoint
                    // (required by global routing health checks)
                    names.add(rotation.rotationId().asString());
                }
                containerEndpoints.add(new ContainerEndpoint(clusterId.value(),
                                                             asString(Endpoint.Scope.global),
                                                             names,
                                                             OptionalInt.empty(),
                                                             endpoint.routingMethod(),
                                                             endpoint.authMethod()));
            }
        });
        endpoints.scope(Endpoint.Scope.application).groupingBy(Endpoint::cluster).forEach((clusterId, clusterEndpoints) -> {
            for (var endpoint : clusterEndpoints) {
                Optional<Endpoint.Target> matchingTarget = endpoint.targets().stream()
                                                                   .filter(t -> t.routesTo(deployment))
                                                                   .findFirst();
                if (matchingTarget.isEmpty()) throw new IllegalStateException("No target found routing to " + deployment + " in " + endpoint);
                containerEndpoints.add(new ContainerEndpoint(clusterId.value(),
                                                             asString(Endpoint.Scope.application),
                                                             List.of(endpoint.dnsName()),
                                                             OptionalInt.of(matchingTarget.get().weight()),
                                                             endpoint.routingMethod(),
                                                             endpoint.authMethod()));
            }
        });
        return containerEndpoints;
    }

    private static String asString(Endpoint.Scope scope) {
        return switch (scope) {
            case application -> "application";
            case global -> "global";
            case weighted -> "weighted";
            case zone -> "zone";
        };
    }

    private static void requireMatchingSans(EndpointCertificate certificate, EndpointList endpoints) {
        for (var endpoint : endpoints.not().scope(Endpoint.Scope.weighted)) { // Weighted endpoints are not present in certificate
            if (!certificate.sanMatches(endpoint.dnsName())) {
                throw new IllegalArgumentException(endpoint + " has no matching SAN. Certificate contains " +
                                                   certificate.requestedDnsSans());
            }
        }
    }

}
