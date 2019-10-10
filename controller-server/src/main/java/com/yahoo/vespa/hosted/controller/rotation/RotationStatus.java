// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.rotation;

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
public class RotationStatus {

    public static final RotationStatus EMPTY = new RotationStatus(Map.of());

    private final Map<RotationId, Targets> status;

    private RotationStatus(Map<RotationId, Targets> status) {
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

    public static RotationStatus from(Map<RotationId, Targets> targets) {
        return targets.isEmpty() ? EMPTY : new RotationStatus(targets);
    }

    /** Targets of a rotation */
    public static class Targets {

        public static final Targets NONE = new Targets(Map.of(), Instant.EPOCH);

        private final Map<ZoneId, RotationState> targets;
        private final Instant lastUpdated;

        public Targets(Map<ZoneId, RotationState> targets, Instant lastUpdated) {
            this.targets = Map.copyOf(Objects.requireNonNull(targets, "states must be non-null"));
            this.lastUpdated = Objects.requireNonNull(lastUpdated, "lastUpdated must be non-null");
        }

        public Map<ZoneId, RotationState> asMap() {
            return targets;
        }

        public Instant lastUpdated() {
            return lastUpdated;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Targets targets1 = (Targets) o;
            return targets.equals(targets1.targets) &&
                   lastUpdated.equals(targets1.lastUpdated);
        }

        @Override
        public int hashCode() {
            return Objects.hash(targets, lastUpdated);
        }

    }

}
