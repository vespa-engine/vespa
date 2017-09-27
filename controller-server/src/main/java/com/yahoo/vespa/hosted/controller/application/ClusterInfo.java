package com.yahoo.vespa.hosted.controller.application;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;

import java.util.List;

/**
 * Value object of static cluster information, in particular the TCO
 * of the hardware used for this cluster.
 *
 * @author smorgrav
 */
public class ClusterInfo {
    private final Flavor flavor;
    private final ClusterSpec.Type clusterType;
    private final List<String> hostnames;

    ClusterInfo(Flavor flavor, ClusterSpec.Type clusterType, List<String> hostnames) {
        this.flavor = flavor;
        this.clusterType = clusterType;
        this.hostnames = hostnames;
    }

    public Flavor getFlavor() {
        return flavor;
    }

    public ClusterSpec.Type getClusterType() {
        return clusterType;
    }

    public List<String> getHostnames() {
        return hostnames;
    }
}
