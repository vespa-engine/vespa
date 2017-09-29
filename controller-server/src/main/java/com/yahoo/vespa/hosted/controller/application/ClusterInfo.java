package com.yahoo.vespa.hosted.controller.application;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.config.provision.ClusterSpec;

import java.util.List;

/**
 * Value object of static cluster information, in particular the TCO
 * of the hardware used for this cluster.
 *
 * @author smorgrav
 */
public class ClusterInfo {
    private final String flavor;
    private final int cost;
    private final ClusterSpec.Type clusterType;
    private final List<String> hostnames;

    public ClusterInfo(String flavor, int cost, ClusterSpec.Type clusterType, List<String> hostnames) {
        this.flavor = flavor;
        this.cost = cost;
        this.clusterType = clusterType;
        this.hostnames = hostnames;
    }

    public String getFlavor() {
        return flavor;
    }

    public int getCost() { return cost; }

    public ClusterSpec.Type getClusterType() {
        return clusterType;
    }

    public List<String> getHostnames() {
        return hostnames;
    }
}
