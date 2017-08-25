// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.rotation;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.log.LogLevel;
import com.yahoo.metrics.simple.Gauge;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.controller.api.identifiers.RotationId;
import com.yahoo.vespa.hosted.controller.api.ApplicationAlias;
import com.yahoo.vespa.hosted.controller.persistence.ControllerDb;
import com.yahoo.vespa.hosted.controller.api.rotation.Rotation;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A rotation repository.
 *
 * @author Oyvind Gronnesby
 */
// TODO: Fold this into ApplicationController+Application
public class ControllerRotationRepository implements RotationRepository {

    private static final Logger log = Logger.getLogger(ControllerRotationRepository.class.getName());

    private static final String REMAINING_ROTATIONS_METRIC_NAME = "remaining_rotations";
    private final Gauge remainingRotations;

    private final ControllerDb controllerDb;
    private final Map<RotationId, Rotation> rotationsMap;

    public ControllerRotationRepository(RotationsConfig rotationConfig, ControllerDb controllerDb, MetricReceiver metricReceiver) {
        this.controllerDb = controllerDb;
        this.rotationsMap = buildRotationsMap(rotationConfig);
        this.remainingRotations = metricReceiver.declareGauge(REMAINING_ROTATIONS_METRIC_NAME);
    }

    private static Map<RotationId, Rotation> buildRotationsMap(RotationsConfig rotationConfig) {
        return rotationConfig.rotations().entrySet().stream()
            .map(entry -> {
                RotationId rotationId = new RotationId(entry.getKey());
                return new Rotation(rotationId, entry.getValue().trim());
            })
            .collect(Collectors.toMap(
                rotation -> rotation.rotationId,
                rotation -> rotation
            ));
    }

    @Override
    @NotNull
    public Set<Rotation> getOrAssignRotation(ApplicationId applicationId, DeploymentSpec deploymentSpec) {
        reportRemainingRotations();

        Set<RotationId> rotations = controllerDb.getRotations(applicationId);

        if (rotations.size() > 1) {
            log.warning(String.format("Application %s has %d > 1 rotation", applicationId, rotations.size()));
        }

        if (!rotations.isEmpty()) {
            return rotations.stream()
                .map(rotationsMap::get)
                .collect(Collectors.toSet());
        }

        if( ! deploymentSpec.globalServiceId().isPresent()) {
            return Collections.emptySet();
        }

        long productionZoneCount = deploymentSpec.zones().stream()
            .filter(zone -> zone.deploysTo(Environment.prod))
            .filter(zone -> ! isCorp(zone)) // Global rotations don't work for nodes in corp network
            .count();

        if (productionZoneCount >= 2) {
            return assignRotation(applicationId);
        }
        else {
            throw new IllegalArgumentException("global-service-id is set but less than 2 prod zones are defined");
        }
    }

    private boolean isCorp(DeploymentSpec.DeclaredZone zone) {
        return zone.region().isPresent() && zone.region().get().value().contains("corp");
    }

    @Override
    @NotNull
    public Set<URI> getRotationUris(ApplicationId applicationId) {
        Set<RotationId> rotations = controllerDb.getRotations(applicationId);
        if (rotations.isEmpty()) {
            return Collections.emptySet();
        } 
        else {
            ApplicationAlias applicationAlias = new ApplicationAlias(applicationId);
            return Collections.singleton(applicationAlias.toHttpUri());
        }
    }

    private Set<Rotation> assignRotation(ApplicationId applicationId) {
        Set<RotationId> availableRotations = availableRotations();
        if (availableRotations.isEmpty()) {
            String message = "Unable to assign global rotation to "
                             + applicationId + " - no rotations available";
            log.info(message);
            throw new RuntimeException(message);
        }

        for (RotationId rotationId : availableRotations) {
            if (controllerDb.assignRotation(rotationId, applicationId)) {
                log.info(String.format("Assigned rotation %s to application %s", rotationId, applicationId));
                Rotation rotation = this.rotationsMap.get(rotationId);
                return Collections.singleton(rotation);
            }
        }

        log.info(String.format("Rotation: No rotations assigned with %s rotations available", availableRotations.size()));
        return Collections.emptySet();
    }

    private Set<RotationId> availableRotations() {
        Set<RotationId> assignedRotations = controllerDb.getRotations();
        Set<RotationId> allRotations = new HashSet<>(rotationsMap.keySet());
        allRotations.removeAll(assignedRotations);
        return allRotations;
    }

    private void reportRemainingRotations() {
        try {
            int freeRotationsCount = availableRotations().size();
            log.log(LogLevel.INFO, "Rotation: {0} global rotations remaining", freeRotationsCount);
            remainingRotations.sample(freeRotationsCount);
        } catch (Exception e) {
            log.log(LogLevel.INFO, "Failed to report rotations metric", e);
        }
    }

}
