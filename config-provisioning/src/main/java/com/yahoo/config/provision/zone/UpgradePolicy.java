// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * This class declares the steps (zones) to follow when upgrading a system. If a step contains multiple zones, those
 * will be upgraded in parallel.
 *
 * @author mpolden
 */
public class UpgradePolicy {

    private final List<Set<ZoneApi>> steps;

    private UpgradePolicy(List<Set<ZoneApi>> steps) {
        for (int i = 0; i < steps.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (!Collections.disjoint(steps.get(i), steps.get(j))) {
                    throw new IllegalArgumentException("One or more zones are declared in multiple steps");
                }
            }
        }
        this.steps = List.copyOf(steps);
    }

    /** Returns the steps in this */
    public List<Set<ZoneApi>> steps() {
        return steps;
    }

    /** Returns a copy of this with the step order inverted */
    public UpgradePolicy inverted() {
        List<Set<ZoneApi>> copy = new ArrayList<>(steps);
        Collections.reverse(copy);
        return new UpgradePolicy(copy);
    }

    public static UpgradePolicy.Builder builder() {
        return new UpgradePolicy.Builder();
    }

    public static class Builder {

        private final List<Set<ZoneApi>> steps = new ArrayList<>();

        private Builder() {}

        /** Upgrade given zone as the next step */
        public Builder upgrade(ZoneApi zone) {
            return upgradeInParallel(zone);
        }

        /** Upgrade given zones in parallel as the next step */
        public Builder upgradeInParallel(ZoneApi... zone) {
            this.steps.add(Set.of(zone));
            return this;
        }

        public UpgradePolicy build() {
            return new UpgradePolicy(steps);
        }

    }

}
