// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing.rotation;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * The status of all rotations assigned to an application.
 *
 * @author mpolden
 */
public record RotationStatus(Map<RotationId, Targets> status) {

    public static final RotationStatus EMPTY = new RotationStatus(Map.of());

    public RotationStatus(Map<RotationId, Targets> status) {
        this.status = Map.copyOf(Objects.requireNonNull(status));
    }

    public Map<RotationId, Targets> asMap() {
        return status;
    }

    /** Get targets of given rotation, if any */
    public Targets of(RotationId rotation) {
        return status.getOrDefault(rotation, Targets.NONE);
    }

    /** Get status of deployment in given rotation, if any */
    public RotationState of(RotationId rotation, Deployment deployment) {
        return of(rotation).asMap().entrySet().stream()
                           .filter(kv -> kv.getKey().equals(deployment.zone()))
                           .map(Map.Entry::getValue)
                           .findFirst()
                           .orElse(RotationState.unknown);
    }

    @Override
    public String toString() {
        return "rotation status " + status;
    }

    public static RotationStatus from(Map<RotationId, Targets> targets) {
        return targets.isEmpty() ? EMPTY : new RotationStatus(targets);
    }

    /** Targets of a rotation */
    public record Targets(Map<ZoneId, RotationState> targets, Instant lastUpdated) {

        public static final Targets NONE = new Targets(Map.of(), Instant.EPOCH);

        public Targets(Map<ZoneId, RotationState> targets, Instant lastUpdated) {
            this.targets = Map.copyOf(Objects.requireNonNull(targets, "states must be non-null"));
            this.lastUpdated = Objects.requireNonNull(lastUpdated, "lastUpdated must be non-null");
        }

        public Map<ZoneId, RotationState> asMap() {
            return targets;
        }

    }

}
