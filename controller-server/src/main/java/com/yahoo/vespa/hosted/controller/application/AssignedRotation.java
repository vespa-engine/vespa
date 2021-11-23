// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationId;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains the tuple of [clusterId, endpointId, rotationId, regions[]], to keep track
 * of which services have assigned which rotations under which name.
 *
 * @author ogronnesby
 */
public class AssignedRotation {
    private final ClusterSpec.Id clusterId;
    private final EndpointId endpointId;
    private final RotationId rotationId;
    private final Set<RegionName> regions;

    public AssignedRotation(ClusterSpec.Id clusterId, EndpointId endpointId, RotationId rotationId, Set<RegionName> regions) {
        this.clusterId = requireNonEmpty(clusterId, clusterId.value(), "clusterId");
        this.endpointId = Objects.requireNonNull(endpointId);
        this.rotationId = Objects.requireNonNull(rotationId);
        this.regions = Set.copyOf(Objects.requireNonNull(regions));
    }

    public ClusterSpec.Id clusterId() { return clusterId; }
    public EndpointId endpointId() { return endpointId; }
    public RotationId rotationId() { return rotationId; }
    public Set<RegionName> regions() { return regions; }

    @Override
    public String toString() {
        return "AssignedRotation{" +
                "clusterId=" + clusterId +
                ", endpointId='" + endpointId + '\'' +
                ", rotationId=" + rotationId +
                ", regions=" + regions +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssignedRotation that = (AssignedRotation) o;
        return clusterId.equals(that.clusterId) &&
                endpointId.equals(that.endpointId) &&
                rotationId.equals(that.rotationId) &&
                regions.equals(that.regions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, endpointId, rotationId, regions);
    }

    private static <T> T requireNonEmpty(T object, String value, String field) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Field '" + field + "' was empty");
        }
        return object;
    }

    /** Convenience method intended for tests */
    public static AssignedRotation fromStrings(String clusterId, String endpointId, String rotationId, Collection<String> regions) {
        return new AssignedRotation(
                new ClusterSpec.Id(clusterId),
                EndpointId.of(endpointId),
                new RotationId(rotationId),
                regions.stream().map(RegionName::from).collect(Collectors.toSet())
        );
    }
}
