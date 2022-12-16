// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationId;

import java.util.Objects;
import java.util.Set;

/**
 * Contains the tuple of [clusterId, endpointId, rotationId, regions[]], to keep track
 * of which services have assigned which rotations under which name.
 *
 * @author ogronnesby
 */
public record AssignedRotation(ClusterSpec.Id clusterId, EndpointId endpointId, RotationId rotationId, Set<RegionName> regions) {

    public AssignedRotation(ClusterSpec.Id clusterId, EndpointId endpointId, RotationId rotationId, Set<RegionName> regions) {
        this.clusterId = requireNonEmpty(clusterId, clusterId.value(), "clusterId");
        this.endpointId = Objects.requireNonNull(endpointId);
        this.rotationId = Objects.requireNonNull(rotationId);
        this.regions = Set.copyOf(Objects.requireNonNull(regions));
    }

    @Override
    public String toString() {
        return "AssignedRotation{" +
               "clusterId=" + clusterId +
               ", endpointId='" + endpointId + '\'' +
               ", rotationId=" + rotationId +
               ", regions=" + regions +
               '}';
    }

    private static <T> T requireNonEmpty(T object, String value, String field) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Field '" + field + "' was empty");
        }
        return object;
    }

}
