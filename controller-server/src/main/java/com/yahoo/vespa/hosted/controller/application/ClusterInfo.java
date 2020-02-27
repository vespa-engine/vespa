// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ClusterSpec;

import java.util.List;

/**
 * Value object of static cluster information, in particular the TCO
 * of the hardware used for this cluster.
 *
 * Some duplication/flattening of flavor info is done to simplify client usage.
 *
 * @author smorgrav
 */
// TODO(mpolden): Remove when we stop writing these fields.
public class ClusterInfo {
    private final String flavor;
    private final double flavorCPU;
    private final double flavorMem;
    private final double flavorDisk;
    private final int flavorCost;
    private final ClusterSpec.Type clusterType;
    private final List<String> hostnames;

    /**
     * @param flavor The name of the flavor eg. 'C-2B/24/500'
     * @param flavorCost The cost of one node in dollars
     * @param flavorCPU The number of cpu cores granted
     * @param flavorMem The memory granted in Gb
     * @param flavorDisk The disk size granted in Gb
     * @param clusterType The vespa cluster type e.g 'container' or 'content'
     * @param hostnames All hostnames in this cluster
     */
    public ClusterInfo(String flavor, int flavorCost, double flavorCPU, double flavorMem,
                       double flavorDisk, ClusterSpec.Type clusterType, List<String> hostnames) {
        this.flavor = flavor;
        this.flavorCost = flavorCost;
        this.flavorCPU = flavorCPU;
        this.flavorMem = flavorMem;
        this.flavorDisk = flavorDisk;
        this.clusterType = clusterType;
        this.hostnames = hostnames;
    }

    /** @return The name of the flavor eg. 'C-2B/24/500' */
    public String getFlavor() {
        return flavor;
    }

    /** @return The cost of one node in dollars */
    public int getFlavorCost() { return flavorCost; }

    /** @return The disk size granted in Gb */
    public double getFlavorDisk() { return flavorDisk; }

    /** @return The number of cpu cores granted */
    public double getFlavorCPU() { return flavorCPU; }

    /** @return The memory granted in Gb */
    public double getFlavorMem() { return flavorMem; }

    /** @return The vespa cluster type e.g 'container' or 'content' */
    public ClusterSpec.Type getClusterType() {
        return clusterType;
    }

    /** @return All hostnames in this cluster */
    public List<String> getHostnames() {
        return hostnames;
    }
}
