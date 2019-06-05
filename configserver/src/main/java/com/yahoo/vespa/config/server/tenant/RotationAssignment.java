// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.Rotation;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.applicationmodel.ClusterId;

import java.util.Objects;

public class RotationAssignment {
    private final String endpointId;
    private final Rotation rotation;
    private final ClusterId containerId;

    RotationAssignment(String endpointId, Rotation rotationName, ClusterId clusterId) {
        this.endpointId = Objects.requireNonNull(endpointId);
        this.rotation = Objects.requireNonNull(rotationName);
        this.containerId = Objects.requireNonNull(clusterId);
    }

    public String getEndpointId() {
        return endpointId;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public ClusterId getContainerId() {
        return containerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RotationAssignment that = (RotationAssignment) o;
        return Objects.equals(endpointId, that.endpointId) &&
                Objects.equals(rotation, that.rotation) &&
                Objects.equals(containerId, that.containerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpointId, rotation, containerId);
    }

    static RotationAssignment fromSlime(Inspector inspector) {
        final var endpointId = inspector.field("endpointId").asString();
        final var rotationId = inspector.field("rotationId").asString();
        final var containerId = inspector.field("containerId").asString();


        if (endpointId.equals("")) {
            throw new IllegalStateException("Missing 'endpointId' in RotationsCache");
        }

        if (rotationId.equals("")) {
            throw new IllegalStateException("Missing 'rotationId' in RotationsCache");
        }

        if (containerId.equals("")) {
            throw new IllegalStateException("Missing 'containerId' in RotationsCache");
        }

        return new RotationAssignment(
                endpointId,
                new Rotation(rotationId),
                new ClusterId(containerId)
        );
    }

    void toSlime(Cursor cursor) {
        cursor.setString("endpointId", endpointId);
        cursor.setString("rotationId", rotation.getId());
        cursor.setString("containerId", containerId.toString());
    }
}
