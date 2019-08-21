// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.rotation;

import com.yahoo.config.application.api.Endpoint;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
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

    /** Get rotation for given application */
    public Optional<Rotation> getRotation(Application application) {
        return application.rotations().stream()
                          .map(AssignedRotation::rotationId)
                          .map(allRotations::get)
                          .findFirst();
    }

    /** Get rotation for the given rotationId */
    public Optional<Rotation> getRotation(RotationId rotationId) {
        return Optional.of(allRotations.get(rotationId));
    }

    /**
     * Returns a rotation for the given application
     *
     * If a rotation is already assigned to the application, that rotation will be returned.
     * If no rotation is assigned, return an available rotation. The caller is responsible for assigning the rotation.
     *
     * @param application The application requesting a rotation
     * @param lock Lock which must be acquired by the caller
     */
    public Rotation getOrAssignRotation(Application application, RotationLock lock) {
        if (! application.rotations().isEmpty()) {
            return allRotations.get(application.rotations().get(0).rotationId());
        }
        if (application.deploymentSpec().globalServiceId().isEmpty()) {
            throw new IllegalArgumentException("global-service-id is not set in deployment spec");
        }
        long productionZones = application.deploymentSpec().zones().stream()
                                          .filter(zone -> zone.deploysTo(Environment.prod))
                                          .count();
        if (productionZones < 2) {
            throw new IllegalArgumentException("global-service-id is set but less than 2 prod zones are defined");
        }
        return findAvailableRotation(application, lock);
    }

    /**
     * Returns rotation assignments for all endpoints in application.
     *
     * If rotations are already assigned, these will be returned.
     * If rotations are not assigned, a new assignment will be created taking new rotations from the repository.
     * This method supports both global-service-id as well as the new endpoints tag.
     *
     * @param application The application requesting rotations.
     * @param lock Lock which by acquired by the caller
     * @return List of rotation assignments - either new or existing.
     */
    public List<AssignedRotation> getOrAssignRotations(Application application, RotationLock lock) {
        if (application.deploymentSpec().globalServiceId().isPresent() && ! application.deploymentSpec().endpoints().isEmpty()) {
            throw new IllegalArgumentException("Cannot provision rotations with both global-service-id and 'endpoints'");
        }

        // Support the older case of setting global-service-id
        if (application.deploymentSpec().globalServiceId().isPresent()) {
            final var regions = application.deploymentSpec().zones().stream()
                    .filter(zone -> zone.environment().isProduction())
                    .flatMap(zone -> zone.region().stream())
                    .collect(Collectors.toSet());

            final var rotation = getOrAssignRotation(application, lock);

            return List.of(
                    new AssignedRotation(
                            new ClusterSpec.Id(application.deploymentSpec().globalServiceId().get()),
                            EndpointId.default_(),
                            rotation.id(),
                            regions
                    )
            );
        }

        final Map<EndpointId, AssignedRotation> existingAssignments = existingEndpointAssignments(application);
        final Map<EndpointId, AssignedRotation> updatedAssignments = assignRotationsToEndpoints(application, existingAssignments, lock);

        existingAssignments.putAll(updatedAssignments);

        return List.copyOf(existingAssignments.values());
    }

    private Map<EndpointId, AssignedRotation> assignRotationsToEndpoints(Application application, Map<EndpointId, AssignedRotation> existingAssignments, RotationLock lock) {
        final var availableRotations = new ArrayList<>(availableRotations(lock).values());

        final var neededRotations = application.deploymentSpec().endpoints().stream()
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

    private Map<EndpointId, AssignedRotation> existingEndpointAssignments(Application application) {
        //
        // Get the regions that has been configured for an endpoint.  Empty set if the endpoint
        // is no longer mentioned in the configuration file.
        //
        final Function<EndpointId, Set<RegionName>> configuredRegionsForEndpoint = endpointId -> {
            return application.deploymentSpec().endpoints().stream()
                    .filter(endpoint -> endpointId.id().equals(endpoint.endpointId()))
                    .map(Endpoint::regions)
                    .findFirst()
                    .orElse(Set.of());
        };

        //
        // Build a new AssignedRotation instance where we update set of regions from the configuration instead
        // of using the one already mentioned in the assignment.  This allows us to overwrite the set of regions
        // when
        final Function<AssignedRotation, AssignedRotation> assignedRotationWithConfiguredRegions = assignedRotation -> {
            return new AssignedRotation(
                    assignedRotation.clusterId(),
                    assignedRotation.endpointId(),
                    assignedRotation.rotationId(),
                    configuredRegionsForEndpoint.apply(assignedRotation.endpointId())
            );
        };

        return application.rotations().stream()
                          .collect(
                                  Collectors.toMap(
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
                                                         .filter(application -> ! application.rotations().isEmpty())
                                                         .flatMap(application -> application.rotations().stream())
                                                         .map(AssignedRotation::rotationId)
                                                         .collect(Collectors.toList());
        Map<RotationId, Rotation> unassignedRotations = new LinkedHashMap<>(this.allRotations);
        assignedRotations.forEach(unassignedRotations::remove);
        return Collections.unmodifiableMap(unassignedRotations);
    }

    private Rotation findAvailableRotation(Application application, RotationLock lock) {
        Map<RotationId, Rotation> availableRotations = availableRotations(lock);
        if (availableRotations.isEmpty()) {
            throw new IllegalStateException("Unable to assign global rotation to " + application.id()
                                            + " - no rotations available");
        }
        // Return first available rotation
        RotationId rotation = availableRotations.keySet().iterator().next();
        log.info(String.format("Offering %s to application %s", rotation, application.id()));
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
