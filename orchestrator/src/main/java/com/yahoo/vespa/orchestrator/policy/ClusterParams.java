// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Parameters controlling orchestration of a particular service cluster of an implied application.
 *
 * @author hakonhall
 */
public class ClusterParams {

    private static final ClusterParams DEFAULT = new ClusterParams.Builder().build();

    private final int size;

    public static class Builder {
        private int size = 0;

        public Builder() {}

        public Builder setSize(int size) {
            this.size = size;
            return this;
        }

        public ClusterParams build() {
            return new ClusterParams(size);
        }
    }

    public static ClusterParams getDefault() {
        return DEFAULT;
    }

    private ClusterParams(int size) {
        this.size = size;
    }

    /**
     * The expected and ideal number of members of the cluster:  Count missing services as down,
     * and expected to be added to the application soon.
     */
    public OptionalInt size() {
        return size > 0 ? OptionalInt.of(size) : OptionalInt.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterParams that = (ClusterParams) o;
        return size == that.size;
    }

    @Override
    public int hashCode() {
        return Objects.hash(size);
    }
}
