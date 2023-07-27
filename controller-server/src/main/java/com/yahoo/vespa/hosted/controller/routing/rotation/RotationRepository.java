// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing.rotation;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.Endpoint;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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

    /** Get rotation with given id */
    public Rotation requireRotation(RotationId id) {
        Rotation rotation = allRotations.get(id);
        if (rotation == null) throw new IllegalArgumentException("No such rotation: '" + id.asString() + "'");
        return rotation;
    }

    /**
     * Returns rotation assignments for all endpoints in application.
     *
     * If rotations are already assigned, these will be returned.
     * If rotations are not assigned, a new assignment will be created taking new rotations from the repository.
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
        var instanceSpec = deploymentSpec.requireInstance(instance.name());
        return assignRotationsTo(instanceSpec.endpoints(), instance, lock);
    }

    private List<AssignedRotation> assignRotationsTo(List<Endpoint> endpoints, Instance instance, RotationLock lock) {
        if (endpoints.isEmpty()) return List.of(); // No endpoints declared, nothing to assign.
        var availableRotations = new ArrayList<>(availableRotations(lock).values());
        var assignedRotationsByEndpointId = instance.rotations().stream()
                                                    .collect(Collectors.toMap(AssignedRotation::endpointId,
                                                                              Function.identity()));
        var assignments = new ArrayList<AssignedRotation>();
        for (var endpoint : endpoints) {
            var endpointId = EndpointId.of(endpoint.endpointId());
            var assignedRotation = assignedRotationsByEndpointId.get(endpointId);
            RotationId rotationId;
            if (assignedRotation == null) { // No rotation is assigned to this endpoint
                rotationId = requireNonEmpty(availableRotations).remove(0).id();
            } else { // Rotation already assigned to this endpoint, reuse it
                rotationId = assignedRotation.rotationId();
            }
            assignments.add(new AssignedRotation(ClusterSpec.Id.from(endpoint.containerId()), endpointId, rotationId, Set.copyOf(endpoint.regions())));
        }
        return Collections.unmodifiableList(assignments);
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
                                                         .toList();
        Map<RotationId, Rotation> unassignedRotations = new LinkedHashMap<>(this.allRotations);
        assignedRotations.forEach(unassignedRotations::remove);
        return Collections.unmodifiableMap(unassignedRotations);
    }

    private Rotation findAvailableRotation(ApplicationId id, RotationLock lock) {
        Map<RotationId, Rotation> availableRotations = availableRotations(lock);
        // Return first available rotation
        RotationId rotation = requireNonEmpty(availableRotations.keySet()).iterator().next();
        log.info(Text.format("Offering %s to application %s", rotation, id));
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

    private static <T extends Collection<?>> T requireNonEmpty(T rotations) {
        if (rotations.isEmpty()) throw new IllegalStateException("Hosted Vespa ran out of rotations, unable to assign rotation");
        return rotations;
    }

}
