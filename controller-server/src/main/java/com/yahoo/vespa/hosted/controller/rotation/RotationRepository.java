// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.rotation;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.Endpoint;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;

/**
 * The rotation repository offers global rotations to Vespa applications.
 *
 * The list of rotations comes from RotationsConfig, which is set in the controller's services.xml.
 *
 * @author Oyvind Gronnesby
 * @author mpolden
 */
public class RotationRepository {

    private static final Logger log = Logger.getLogger(RotationRepository.class.getName());

    private final Map<RotationId, Rotation> allRotations;
    private final ApplicationController applications;
    private final CuratorDb curator;

    public RotationRepository(RotationsConfig rotationsConfig, ApplicationController applications, CuratorDb curator) {
        this.allRotations = from(rotationsConfig);
        this.applications = applications;
        this.curator = curator;
    }

    /** Acquire a exclusive lock for this */
    public RotationLock lock() {
        return new RotationLock(curator.lockRotations());
    }

    /** Get rotation by given rotationId */
    public Optional<Rotation> getRotation(RotationId rotationId) {
        return Optional.of(allRotations.get(rotationId));
    }

    /**
     * Returns a single rotation for the given application. This is only used when a rotation is assigned through the
     * use of a global service ID.
     *
     * If a rotation is already assigned to the application, that rotation will be returned.
     * If no rotation is assigned, return an available rotation. The caller is responsible for assigning the rotation.
     *
     * @param deploymentSpec the deployment spec for the application
     * @param instance the instance requesting a rotation
     * @param lock lock which must be acquired by the caller
     */
    private Rotation getOrAssignRotation(DeploymentSpec deploymentSpec, Instance instance, RotationLock lock) {
        if ( ! instance.rotations().isEmpty()) {
            return allRotations.get(instance.rotations().get(0).rotationId());
        }

        if (deploymentSpec.requireInstance(instance.name()).globalServiceId().isEmpty()) {
            throw new IllegalArgumentException("global-service-id is not set in deployment spec for instance '" +
                                               instance.name() + "'");
        }
        long productionZones = deploymentSpec.requireInstance(instance.name()).zones().stream()
                                                     .filter(zone -> zone.concerns(Environment.prod))
                                                     .count();
        if (productionZones < 2) {
            throw new IllegalArgumentException("global-service-id is set but less than 2 prod zones are defined " +
                                               "in instance '" + instance.name() + "'");
        }
        return findAvailableRotation(instance.id(), lock);
    }

    /**
     * Returns rotation assignments for all endpoints in application.
     *
     * If rotations are already assigned, these will be returned.
     * If rotations are not assigned, a new assignment will be created taking new rotations from the repository.
     * This method supports both global-service-id as well as the new endpoints tag.
     *
     * @param deploymentSpec The deployment spec of the application
     * @param instance The application requesting rotations
     * @param lock Lock which by acquired by the caller
     * @return List of rotation assignments - either new or existing
     */
    public List<AssignedRotation> getOrAssignRotations(DeploymentSpec deploymentSpec, Instance instance, RotationLock lock) {
        // Skip assignment if no rotations are configured in this system
        if (allRotations.isEmpty()) {
            return List.of();
        }

        // Only allow one kind of configuration syntax
        if (     deploymentSpec.requireInstance(instance.name()).globalServiceId().isPresent()
            && ! deploymentSpec.requireInstance(instance.name()).endpoints().isEmpty()) {
            throw new IllegalArgumentException("Cannot provision rotations with both global-service-id and 'endpoints'");
        }

        // Support the older case of setting global-service-id
        if (deploymentSpec.requireInstance(instance.name()).globalServiceId().isPresent()) {
            var regions = deploymentSpec.requireInstance(instance.name()).zones().stream()
                                                .filter(zone -> zone.environment().isProduction())
                                                .flatMap(zone -> zone.region().stream())
                                                .collect(Collectors.toSet());

            var rotation = getOrAssignRotation(deploymentSpec, instance, lock);

            return List.of(
                    new AssignedRotation(
                            new ClusterSpec.Id(deploymentSpec.requireInstance(instance.name()).globalServiceId().get()),
                            EndpointId.defaultId(),
                            rotation.id(),
                            regions
                    )
            );
        }

        Map<EndpointId, AssignedRotation> existingAssignments = existingEndpointAssignments(deploymentSpec, instance);
        Map<EndpointId, AssignedRotation> updatedAssignments = assignRotationsToEndpoints(deploymentSpec, existingAssignments, instance.name(), lock);

        existingAssignments.putAll(updatedAssignments);

        return List.copyOf(existingAssignments.values());
    }

    private Map<EndpointId, AssignedRotation> assignRotationsToEndpoints(DeploymentSpec deploymentSpec,
                                                                         Map<EndpointId, AssignedRotation> existingAssignments,
                                                                         InstanceName instance,
                                                                         RotationLock lock) {
        var availableRotations = new ArrayList<>(availableRotations(lock).values());

        var neededRotations = deploymentSpec.requireInstance(instance).endpoints().stream()
                                            .filter(Predicate.not(endpoint -> existingAssignments.containsKey(EndpointId.of(endpoint.endpointId()))))
                                            .collect(Collectors.toSet());

        if (neededRotations.size() > availableRotations.size()) {
            throw new IllegalStateException("Hosted Vespa ran out of rotations, unable to assign rotation: need " + neededRotations.size() + ", have " + availableRotations.size());
        }

        return neededRotations.stream()
                .map(endpoint -> {
                        return new AssignedRotation(
                                new ClusterSpec.Id(endpoint.containerId()),
                                EndpointId.of(endpoint.endpointId()),
                                availableRotations.remove(0).id(),
                                endpoint.regions()
                        );
                })
                .collect(
                        Collectors.toMap(
                                AssignedRotation::endpointId,
                                Function.identity(),
                                (a, b) -> { throw new IllegalStateException("Duplicate entries:" + a + ", " + b); },
                                LinkedHashMap::new
                        )
                );
    }

    private Map<EndpointId, AssignedRotation> existingEndpointAssignments(DeploymentSpec deploymentSpec, Instance instance) {
        // Get the regions that has been configured for an endpoint.  Empty set if the endpoint
        // is no longer mentioned in the configuration file.
        Function<EndpointId, Set<RegionName>> configuredRegionsForEndpoint = endpointId ->
            deploymentSpec.requireInstance(instance.name()).endpoints().stream()
                                 .filter(endpoint -> endpointId.id().equals(endpoint.endpointId()))
                                 .map(Endpoint::regions)
                                 .findFirst()
                                 .orElse(Set.of());

        // Build a new AssignedRotation instance where we update set of regions from the configuration instead
        // of using the one already mentioned in the assignment.  This allows us to overwrite the set of regions.
        Function<AssignedRotation, AssignedRotation> assignedRotationWithConfiguredRegions = assignedRotation ->
            new AssignedRotation(
                    assignedRotation.clusterId(),
                    assignedRotation.endpointId(),
                    assignedRotation.rotationId(),
                    configuredRegionsForEndpoint.apply(assignedRotation.endpointId()));

        return instance.rotations().stream()
                       .collect(Collectors.toMap(
                                          AssignedRotation::endpointId,
                                          assignedRotationWithConfiguredRegions,
                                          (a, b) -> {
                                              throw new IllegalStateException("Duplicate entries: " + a + ", " + b);
                                          },
                                          LinkedHashMap::new
                                  )
                          );
    }

    /**
     * Returns all unassigned rotations
     * @param lock Lock which must be acquired by the caller
     */
    public Map<RotationId, Rotation> availableRotations(@SuppressWarnings("unused") RotationLock lock) {
        List<RotationId> assignedRotations = applications.asList().stream()
                                                         .flatMap(application -> application.instances().values().stream())
                                                         .flatMap(instance -> instance.rotations().stream())
                                                         .map(AssignedRotation::rotationId)
                                                         .collect(Collectors.toList());
        Map<RotationId, Rotation> unassignedRotations = new LinkedHashMap<>(this.allRotations);
        assignedRotations.forEach(unassignedRotations::remove);
        return Collections.unmodifiableMap(unassignedRotations);
    }

    private Rotation findAvailableRotation(ApplicationId id, RotationLock lock) {
        Map<RotationId, Rotation> availableRotations = availableRotations(lock);
        if (availableRotations.isEmpty()) {
            throw new IllegalStateException("Unable to assign global rotation to " + id
                                            + " - no rotations available");
        }
        // Return first available rotation
        RotationId rotation = availableRotations.keySet().iterator().next();
        log.info(String.format("Offering %s to application %s", rotation, id));
        return allRotations.get(rotation);
    }

    /** Returns a immutable map of rotation ID to rotation sorted by rotation ID */
    private static Map<RotationId, Rotation> from(RotationsConfig rotationConfig) {
        return rotationConfig.rotations().entrySet().stream()
                             .map(entry -> new Rotation(new RotationId(entry.getKey()), entry.getValue().trim()))
                             .sorted(Comparator.comparing(rotation -> rotation.id().asString()))
                             .collect(collectingAndThen(Collectors.toMap(Rotation::id,
                                                                         rotation -> rotation,
                                                                         (k, v) -> v,
                                                                         LinkedHashMap::new),
                                                        Collections::unmodifiableMap));
    }

}
