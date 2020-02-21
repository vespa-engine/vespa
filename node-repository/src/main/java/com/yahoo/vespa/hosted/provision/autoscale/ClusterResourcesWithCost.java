// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

/**
 * @author bratseth
 */
public class ClusterResourcesWithCost {

    private final ClusterResources resources;
    private final double cost;

    public ClusterResourcesWithCost(ClusterResources resources, double cost) {
        this.resources = resources;
        this.cost = cost;
    }

    public ClusterResources clusterResources() { return resources;}

    public double cost() { return cost; }

    @Override
    public String toString() {
        return "$" + cost + ": " + clusterResources();
    }

}
