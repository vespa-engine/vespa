// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.rotation;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.RotationId;
import com.yahoo.vespa.hosted.controller.api.rotation.Rotation;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A rotation repository backed by in-memory data structures
 * 
 * @author bratseth
 */
public class MemoryRotationRepository implements RotationRepository {

    private final Map<ApplicationId, Set<Rotation>> rotations = new HashMap<>();
    
    @NotNull
    @Override
    public Set<Rotation> getOrAssignRotation(ApplicationId application, DeploymentSpec deploymentSpec) {
        if (rotations.containsKey(application)) {
            return rotations.get(application);
        }
        Set<Rotation> rotations = ImmutableSet.of(new Rotation(
                new RotationId("generated-by-routing-service-" + UUID.randomUUID().toString()),
                "fake-global-rotation-" + application.toShortString())
        );
        this.rotations.put(application, rotations);
        return rotations;
    }

    @NotNull
    @Override
    public Set<URI> getRotationUris(ApplicationId applicationId) {
        Set<Rotation> rotations = this.rotations.get(applicationId);
        if (rotations == null) {
            return Collections.emptySet();
        }
        return rotations.stream()
                .map(rotation -> URI.create("http://" + rotation.rotationName))
                .collect(Collectors.toSet());
    }

}
