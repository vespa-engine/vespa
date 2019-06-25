package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;

import java.util.Objects;

/**
 * Contains the tuple of [clusterId, endpointId, rotationId], to keep track
 * of which services have assigned which rotations under which name.
 *
 * @author ogronnesby
 */
public class AssignedRotation {
    private final ClusterSpec.Id clusterId;
    private final EndpointId endpointId;
    private final RotationId rotationId;

    public AssignedRotation(ClusterSpec.Id clusterId, EndpointId endpointId, RotationId rotationId) {
        this.clusterId = requireNonEmpty(clusterId, clusterId.value(), "clusterId");
        this.endpointId = Objects.requireNonNull(endpointId);
        this.rotationId = Objects.requireNonNull(rotationId);
    }

    public ClusterSpec.Id clusterId() { return clusterId; }
    public EndpointId endpointId() { return endpointId; }
    public RotationId rotationId() { return rotationId; }

    @Override
    public String toString() {
        return "AssignedRotation{" +
                "clusterId=" + clusterId +
                ", endpointId='" + endpointId + '\'' +
                ", rotationId=" + rotationId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssignedRotation that = (AssignedRotation) o;
        return clusterId.equals(that.clusterId) &&
                endpointId.equals(that.endpointId) &&
                rotationId.equals(that.rotationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, endpointId, rotationId);
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
