// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * This class declares the steps (zones) to follow when upgrading a system. If a step contains multiple zones, those
 * will be upgraded in parallel.
 *
 * @author mpolden
 */
public record UpgradePolicy(List<Step> steps) {

    public UpgradePolicy(List<Step> steps) {
        Objects.requireNonNull(steps);
        for (int i = 0; i < steps.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (!Collections.disjoint(steps.get(i).zones(), steps.get(j).zones())) {
                    throw new IllegalArgumentException("One or more zones are declared in multiple steps");
                }
            }
        }
        this.steps = List.copyOf(steps);
    }

    /** Returns a copy of this with the step order inverted */
    public UpgradePolicy inverted() {
        List<Step> copy = new ArrayList<>(steps);
        Collections.reverse(copy);
        return new UpgradePolicy(copy);
    }

    public static UpgradePolicy.Builder builder() {
        return new UpgradePolicy.Builder();
    }

    public record Builder(List<Step> steps) {

        private Builder() {
            this(new ArrayList<>());
        }

        public Builder upgrade(Step step) {
            this.steps.add(step);
            return this;
        }

        public Builder upgrade(ZoneApi zone) {
            return upgradeInParallel(zone);
        }

        public Builder upgradeInParallel(ZoneApi... zone) {
            return upgrade(Step.of(zone));
        }

        public UpgradePolicy build() {
            return new UpgradePolicy(steps);
        }

    }

    /**
     * An upgrade step, consisting of one or more zones. If a step contains multiple zones, those will be upgraded in
     * parallel.
     */
    public record Step(Set<ZoneApi> zones, NodeSlice nodeSlice) {

        public Step(Set<ZoneApi> zones, NodeSlice nodeSlice) {
            if (zones.isEmpty()) throw new IllegalArgumentException("A step must contain at least one zone");
            this.zones = Set.copyOf(Objects.requireNonNull(zones));
            this.nodeSlice = Objects.requireNonNull(nodeSlice);
        }

        /** Create a step for given zones, which requires all nodes to complete upgrade */
        public static Step of(ZoneApi... zone) {
            return new Step(Set.of(zone), NodeSlice.ALL);
        }

        /** Returns a copy of this step, requiring only the given slice of nodes for each zone in this step to upgrade */
        public Step require(NodeSlice slice) {
            return new Step(zones, slice);
        }

    }

}
