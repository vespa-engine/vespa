// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
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
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.rotation.RotationRepository;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicies;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private static final Logger log = Logger.getLogger(RoutingController.class.getName());

    private final Controller controller;
    private final RoutingPolicies routingPolicies;
    private final RotationRepository rotationRepository;
    private final RoutingGenerator routingGenerator;

    public RoutingController(Controller controller, RotationsConfig rotationsConfig) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
        this.routingPolicies = new RoutingPolicies(controller);
        this.rotationRepository = new RotationRepository(rotationsConfig, controller.applications(),
                                                         controller.curator());
        this.routingGenerator = controller.serviceRegistry().routingGenerator();
    }

    public RoutingPolicies policies() {
        return routingPolicies;
    }

    public RotationRepository rotations() {
        return rotationRepository;
    }

    /** Returns all legacy endpoint URLs for given deployment, including global, in the shared routing layer */
    public List<URI> legacyEndpointsOf(DeploymentId deployment) {
        return routingEndpointsOf(deployment).stream()
                                             .map(RoutingEndpoint::endpoint)
                                             .map(URI::create)
                                             .collect(Collectors.toUnmodifiableList());
    }

    /** Returns legacy zone endpoints for given deployment, in the shared routing layer */
    public Map<ClusterSpec.Id, URI> legacyZoneEndpointsOf(DeploymentId deployment) {
        if (!supportsRoutingMethod(RoutingMethod.shared, deployment.zoneId())) {
            return Map.of();
        }
        try {
            return routingGenerator.clusterEndpoints(deployment);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to get endpoint information for " + deployment, e);
            return Map.of();
        }
    }

    /**
     * Returns all non-global endpoint URLs for given deployment, grouped by their cluster ID. If deployment supports
     * {@link RoutingMethod#exclusive} endpoints defined through routing polices are returned.
     */
    public Map<ClusterSpec.Id, URI> zoneEndpointsOf(DeploymentId deployment) {
        if ( ! controller.applications().getInstance(deployment.applicationId())
                .map(application -> application.deployments().containsKey(deployment.zoneId()))
                .orElse(deployment.applicationId().instance().isTester()))
            throw new NotExistsException("Deployment", deployment.toString());

        // In exclusively routed zones we create endpoint URLs from routing policies
        if (supportsRoutingMethod(RoutingMethod.exclusive, deployment.zoneId())) {
            return routingPolicies.get(deployment).values().stream()
                                  .filter(policy -> policy.endpointIn(controller.system()).scope() == Endpoint.Scope.zone)
                                  .collect(Collectors.toUnmodifiableMap(policy -> policy.id().cluster(),
                                                                        policy -> policy.endpointIn(controller.system())
                                                                                        .url()));
        }
        return legacyZoneEndpointsOf(deployment);
    }

    /** Returns all non-global endpoint URLs for given deployments, grouped by their cluster ID and zone */
    public Map<ZoneId, Map<ClusterSpec.Id, URI>> zoneEndpointsOf(Collection<DeploymentId> deployments) {
        var endpoints = new TreeMap<ZoneId, Map<ClusterSpec.Id, URI>>(Comparator.comparing(ZoneId::value));
        for (var deployment : deployments) {
            var zoneEndpoints = zoneEndpointsOf(deployment);
            if (!zoneEndpoints.isEmpty()) {
                endpoints.put(deployment.zoneId(), zoneEndpoints);
            }
        }
        return Collections.unmodifiableMap(endpoints);
    }

    /** Change status of all global endpoints for given deployment */
    public void setGlobalRotationStatus(DeploymentId deployment, EndpointStatus status) {
        var globalEndpoints = legacyGlobalEndpointsOf(deployment);
        globalEndpoints.forEach(endpoint -> {
            try {
                controller.serviceRegistry().configServer().setGlobalRotationStatus(deployment, endpoint.upstreamName(), status);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set rotation status of " + endpoint + " in " + deployment, e);
            }
        });
    }

    /** Get global endpoint status for given deployment */
    public Map<RoutingEndpoint, EndpointStatus> globalRotationStatus(DeploymentId deployment) {
        var routingEndpoints = new LinkedHashMap<RoutingEndpoint, EndpointStatus>();
        legacyGlobalEndpointsOf(deployment).forEach(endpoint -> {
            var status = controller.serviceRegistry().configServer().getGlobalRotationStatus(deployment, endpoint.upstreamName());
            routingEndpoints.put(endpoint, status);
        });
        return Collections.unmodifiableMap(routingEndpoints);
    }

    /** Find the global endpoints of given deployment */
    private List<RoutingEndpoint> legacyGlobalEndpointsOf(DeploymentId deployment) {
        return routingEndpointsOf(deployment).stream()
                                             .filter(RoutingEndpoint::isGlobal)
                                             .collect(Collectors.toUnmodifiableList());
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
            var endpoints = instance.endpointsIn(controller.system(), assignedRotation.endpointId())
                                    .scope(Endpoint.Scope.global);

            // Skip rotations which do not apply to this zone. Legacy names always point to all zones
            if (!registerLegacyNames && !assignedRotation.regions().contains(zone.region())) {
                continue;
            }

            // Omit legacy DNS names when assigning rotations using <endpoints/> syntax
            if (!registerLegacyNames) {
                endpoints = endpoints.legacy(false);
            }

            // Register names in DNS
            var rotation = rotationRepository.getRotation(assignedRotation.rotationId());
            if (rotation.isPresent()) {
                endpoints.asList().forEach(endpoint -> {
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
        instance.rotations().forEach(assignedRotation -> {
            var endpoints = instance.endpointsIn(controller.system(), assignedRotation.endpointId());
            endpoints.asList().stream()
                     .map(Endpoint::dnsName)
                     .forEach(name -> {
                         controller.nameServiceForwarder().removeRecords(Record.Type.CNAME, RecordName.from(name),
                                                                         Priority.normal);
                     });
        });
    }

    private List<RoutingEndpoint> routingEndpointsOf(DeploymentId deployment) {
        if (!supportsRoutingMethod(RoutingMethod.shared, deployment.zoneId())) {
            return List.of(); // No rotations/shared routing layer in this zone.
        }
        try {
            return routingGenerator.endpoints(deployment);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to get endpoints for " + deployment, e);
            return List.of();
        }
    }

    /** Returns whether given routingMethod is supported by zone */
    public boolean supportsRoutingMethod(RoutingMethod routingMethod, ZoneId zone) {
        return controller.zoneRegistry().zones().routingMethod(routingMethod).ids().contains(zone);
    }

}
