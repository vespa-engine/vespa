// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Parameters controlling orchestration of a particular service cluster of an implied application.
 *
 * @author hakonhall
 */
public class ClusterParams {

    private static final ClusterParams DEFAULT = new ClusterParams.Builder().build();

    private final int size;
    private final int allowedDown;
    private final double allowedDownRatio;

    public static class Builder {
        private int size = -1;
        private int allowedDown = -1;
        private double allowedDownRatio = -1.0;

        public Builder() {}

        public Builder setSize(int size) {
            this.size = size;
            return this;
        }

        public Builder setAllowedDown(int allowedDown) {
            this.allowedDown = allowedDown;
            return this;
        }

        public Builder setAllowedDownRatio(double allowedDownRatio) {
            this.allowedDownRatio = allowedDownRatio;
            return this;
        }

        public ClusterParams build() {
            return new ClusterParams(size, allowedDown, allowedDownRatio);
        }
    }

    public static ClusterParams getDefault() {
        return DEFAULT;
    }

    private ClusterParams(int size, int allowedDown, double allowedDownRatio) {
        this.size = size;
        this.allowedDown = allowedDown;
        this.allowedDownRatio = allowedDownRatio;
    }

    /**
     * The expected and ideal number of members of the cluster:  Count missing services as down,
     * and expected to be added to the application soon.
     */
    public OptionalInt size() {
        return size > 0 ? OptionalInt.of(size) : OptionalInt.empty();
    }

    /** The number of services that are allowed to be down. */
    public OptionalInt allowedDown() {
        return allowedDown > 0 ? OptionalInt.of(allowedDown) : OptionalInt.empty();
    }

    /** The ratio of services that are allowed to be down. */
    public OptionalDouble allowedDownRatio() {
        return 0.0 <= allowedDownRatio && allowedDownRatio <= 1.0 ?
               OptionalDouble.of(allowedDownRatio) :
               OptionalDouble.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterParams that = (ClusterParams) o;
        return size == that.size && allowedDown == that.allowedDown && Double.compare(that.allowedDownRatio, allowedDownRatio) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, allowedDown, allowedDownRatio);
    }
}
