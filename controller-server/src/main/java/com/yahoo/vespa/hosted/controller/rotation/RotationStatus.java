// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.rotation;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * The status of all rotations assigned to an application.
 *
 * @author mpolden
 */
public class RotationStatus {

    public static final RotationStatus EMPTY = new RotationStatus(Map.of());

    private final Map<RotationId, Map<ZoneId, RotationState>> status;

    /** DO NOT USE. Public for serialization purposes */
    public RotationStatus(Map<RotationId, Map<ZoneId, RotationState>> status) {
        this.status = Map.copyOf(Objects.requireNonNull(status));
    }

    public Map<RotationId, Map<ZoneId, RotationState>> asMap() {
        return status;
    }

    /** Get status of given rotation, if any */
    public Map<ZoneId, RotationState> of(RotationId rotation) {
        return status.getOrDefault(rotation, Map.of());
    }

    /** Get status of given deployment, if any */
    public RotationState of(Deployment deployment) {
        return status.values().stream()
                     .map(Map::entrySet)
                     .flatMap(Collection::stream)
                     // TODO(mpolden): Change to exact comparison after September 2019
                     .filter(kv -> kv.getKey().value().contains(deployment.zone().value()))
                     .map(Map.Entry::getValue)
                     .findFirst()
                     .orElse(RotationState.unknown);
    }

    @Override
    public String toString() {
        return "rotation status " + status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RotationStatus that = (RotationStatus) o;
        return status.equals(that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status);
    }

}
