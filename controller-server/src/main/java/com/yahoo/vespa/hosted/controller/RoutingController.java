// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import com.yahoo.vespa.hosted.controller.application.EndpointList;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.rotation.RotationRepository;
import com.yahoo.vespa.hosted.controller.routing.RoutingId;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicies;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The routing controller encapsulates state and methods for inspecting and manipulating DNS-level routing of traffic
 * in a system.
 *
 * The one stop shop for all your routing needs!
 *
 * @author mpolden
 */
public class RoutingController {

    private final Controller controller;
    private final RoutingPolicies routingPolicies;
    private final RotationRepository rotationRepository;

    public RoutingController(Controller controller, RotationsConfig rotationsConfig) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
        this.routingPolicies = new RoutingPolicies(controller);
        this.rotationRepository = new RotationRepository(rotationsConfig, controller.applications(),
                                                         controller.curator());
    }

    public RoutingPolicies policies() {
        return routingPolicies;
    }

    public RotationRepository rotations() {
        return rotationRepository;
    }

    /** Returns zone-scoped endpoints for given deployment */
    public EndpointList endpointsOf(DeploymentId deployment) {
        var endpoints = new LinkedHashSet<Endpoint>();
        for (var policy : routingPolicies.get(deployment).values()) {
            if (!policy.status().isActive()) continue;
            for (var routingMethod :  controller.zoneRegistry().routingMethods(policy.id().zone())) {
                endpoints.add(policy.endpointIn(controller.system(), routingMethod));
            }
        }
        if (endpoints.isEmpty()) { // TODO(mpolden): Remove this once all applications have deployed once
            controller.serviceRegistry().routingGenerator().clusterEndpoints(deployment)
                      .forEach((cluster, url) -> endpoints.add(Endpoint.of(deployment.applicationId())
                                                                       .target(cluster, deployment.zoneId())
                                                                       .routingMethod(RoutingMethod.shared)
                                                                       .on(Port.fromRoutingMethod(RoutingMethod.shared))
                                                                       .in(controller.system())));
        }
        return EndpointList.copyOf(endpoints);
    }

    /** Returns global-scoped endpoints for given instance */
    public EndpointList endpointsOf(ApplicationId instance) {
        return endpointsOf(controller.applications().requireInstance(instance));
    }

    /** Returns global-scoped endpoints for given instance */
    // TODO(mpolden): Add a endpointsOf(Instance, DeploymentId) variant of this that only returns global endpoint of
    //                which deployment is a member
    public EndpointList endpointsOf(Instance instance) {
        var endpoints = new LinkedHashSet<Endpoint>();
        // Add global endpoints provided by rotations
        for (var rotation : instance.rotations()) {
            EndpointList.global(RoutingId.of(instance.id(), rotation.endpointId()),
                                controller.system(), systemRoutingMethods())
                        .requiresRotation()
                        .forEach(endpoints::add);
        }
        // Add global endpoints provided by routing policices
        for (var policy : routingPolicies.get(instance.id()).values()) {
            if (!policy.status().isActive()) continue;
            for (var endpointId : policy.endpoints()) {
                EndpointList.global(RoutingId.of(instance.id(), endpointId),
                                    controller.system(), systemRoutingMethods())
                            .not().requiresRotation()
                            .forEach(endpoints::add);
            }
        }
        return EndpointList.copyOf(endpoints);
    }

    /** Returns all non-global endpoints and corresponding cluster IDs for given deployments, grouped by their zone */
    public Map<ZoneId, Map<URI, ClusterSpec.Id>> zoneEndpointsOf(Collection<DeploymentId> deployments) {
        var endpoints = new TreeMap<ZoneId, Map<URI, ClusterSpec.Id>>(Comparator.comparing(ZoneId::value));
        for (var deployment : deployments) {
            var zoneEndpoints = endpointsOf(deployment).scope(Endpoint.Scope.zone).asList().stream()
                                                       .collect(Collectors.toUnmodifiableMap(Endpoint::url,
                                                                                             endpoint -> ClusterSpec.Id.from(endpoint.name())));
            if (!zoneEndpoints.isEmpty()) {
                endpoints.put(deployment.zoneId(), zoneEndpoints);
            }
        }
        return Collections.unmodifiableMap(endpoints);
    }

    /** Change status of all global endpoints for given deployment */
    public void setGlobalRotationStatus(DeploymentId deployment, EndpointStatus status) {
        endpointsOf(deployment.applicationId()).requiresRotation().primary().ifPresent(endpoint -> {
            try {
                controller.serviceRegistry().configServer().setGlobalRotationStatus(deployment, endpoint.upstreamIdOf(deployment), status);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set rotation status of " + endpoint + " in " + deployment, e);
            }
        });
    }

    /** Get global endpoint status for given deployment */
    public Map<Endpoint, EndpointStatus> globalRotationStatus(DeploymentId deployment) {
        var routingEndpoints = new LinkedHashMap<Endpoint, EndpointStatus>();
        endpointsOf(deployment.applicationId()).requiresRotation().primary().ifPresent(endpoint -> {
            var upstreamName = endpoint.upstreamIdOf(deployment);
            var status = controller.serviceRegistry().configServer().getGlobalRotationStatus(deployment, upstreamName);
            routingEndpoints.put(endpoint, status);
        });
        return Collections.unmodifiableMap(routingEndpoints);
    }

    /**
     * Assigns one or more global rotations to given application, if eligible. The given application is implicitly
     * stored, ensuring that the assigned rotation(s) are persisted when this returns.
     */
    public LockedApplication assignRotations(LockedApplication application, InstanceName instanceName) {
        try (RotationLock rotationLock = rotationRepository.lock()) {
            var rotations = rotationRepository.getOrAssignRotations(application.get().deploymentSpec(),
                                                                    application.get().require(instanceName),
                                                                    rotationLock);
            application = application.with(instanceName, instance -> instance.with(rotations));
            controller.applications().store(application); // store assigned rotation even if deployment fails
        }
        return application;
    }

    /**
     * Register endpoints for rotations assigned to given application and zone in DNS.
     *
     * @return the registered endpoints
     */
    public Set<ContainerEndpoint> registerEndpointsInDns(DeploymentSpec deploymentSpec, Instance instance, ZoneId zone) {
        var containerEndpoints = new HashSet<ContainerEndpoint>();
        boolean registerLegacyNames = deploymentSpec.instance(instance.name())
                                                    .flatMap(DeploymentInstanceSpec::globalServiceId)
                                                    .isPresent();
        for (var assignedRotation : instance.rotations()) {
            var names = new ArrayList<String>();
            var endpoints = endpointsOf(instance).named(assignedRotation.endpointId()).requiresRotation();

            // Skip rotations which do not apply to this zone. Legacy names always point to all zones
            if (!registerLegacyNames && !assignedRotation.regions().contains(zone.region())) {
                continue;
            }

            // Omit legacy DNS names when assigning rotations using <endpoints/> syntax
            if (!registerLegacyNames) {
                endpoints = endpoints.not().legacy();
            }

            // Register names in DNS
            var rotation = rotationRepository.getRotation(assignedRotation.rotationId());
            if (rotation.isPresent()) {
                endpoints.forEach(endpoint -> {
                    controller.nameServiceForwarder().createCname(RecordName.from(endpoint.dnsName()),
                                                                  RecordData.fqdn(rotation.get().name()),
                                                                  Priority.normal);
                    names.add(endpoint.dnsName());
                });
            }

            // Include rotation ID as a valid name of this container endpoint (required by global routing health checks)
            names.add(assignedRotation.rotationId().asString());
            containerEndpoints.add(new ContainerEndpoint(assignedRotation.clusterId().value(), names));
        }
        return Collections.unmodifiableSet(containerEndpoints);
    }

    /** Remove endpoints in DNS for all rotations assigned to given instance */
    public void removeEndpointsInDns(Instance instance) {
        endpointsOf(instance).requiresRotation()
                             .forEach(endpoint -> controller.nameServiceForwarder()
                                                            .removeRecords(Record.Type.CNAME,
                                                                           RecordName.from(endpoint.dnsName()),
                                                                           Priority.normal));
    }

    /** Returns all routing methods supported by this system */
    private List<RoutingMethod> systemRoutingMethods() {
        return controller.zoneRegistry().zones().all().ids().stream()
                         .flatMap(zone -> controller.zoneRegistry().routingMethods(zone).stream())
                         .distinct()
                         .collect(Collectors.toUnmodifiableList());
    }

}
