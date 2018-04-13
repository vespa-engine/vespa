// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.rotation;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        return application.rotation().map(r -> allRotations.get(r.id()));
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
        if (application.rotation().isPresent()) {
            return allRotations.get(application.rotation().get().id());
        }
        if (!application.deploymentSpec().globalServiceId().isPresent()) {
            throw new IllegalArgumentException("global-service-id is not set in deployment spec");
        }
        long productionZones = application.deploymentSpec().zones().stream()
                                          .filter(zone -> zone.deploysTo(Environment.prod))
                                          // Global rotations don't work for nodes in corp network
                                          .filter(zone -> !isCorp(zone))
                                          .count();
        if (productionZones < 2) {
            throw new IllegalArgumentException("global-service-id is set but less than 2 prod zones are defined");
        }
        return findAvailableRotation(application, lock);
    }

    /**
     * Returns all unassigned rotations
     * @param lock Lock which must be acquired by the caller
     */
    public Map<RotationId, Rotation> availableRotations(@SuppressWarnings("unused") RotationLock lock) {
        List<RotationId> assignedRotations = applications.asList().stream()
                                                         .filter(application -> application.rotation().isPresent())
                                                         .map(application -> application.rotation().get().id())
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

    private static boolean isCorp(DeploymentSpec.DeclaredZone zone) {
        return zone.region().isPresent() && zone.region().get().value().contains("corp");
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
