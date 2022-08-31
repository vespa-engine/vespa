// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Objects;

/**
 * Uniquely identifies a running FleetController: cluster name + index.
 *
 * @author hakon
 */
public class FleetControllerId {
    private final String clusterName;
    private final int index;

    public static FleetControllerId fromOptions(FleetControllerOptions options) {
        return new FleetControllerId(options.clusterName(), options.fleetControllerIndex());
    }

    public FleetControllerId(String clusterName, int index) {
        this.clusterName = clusterName;
        this.index = index;
    }

    public String clusterName() { return clusterName; }
    public int index() { return index; }

    @Override
    public String toString() {
        return "FleetController " + index + " for cluster '" + clusterName + '\'';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FleetControllerId that = (FleetControllerId) o;
        return index == that.index && Objects.equals(clusterName, that.clusterName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterName, index);
    }
}
