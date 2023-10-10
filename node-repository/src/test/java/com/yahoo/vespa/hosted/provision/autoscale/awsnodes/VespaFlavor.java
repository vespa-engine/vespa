// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale.awsnodes;

import com.yahoo.config.provision.NodeResources;

/**
 * Holds the advertised and real resources of a host type.
 *
 * @author bratseth
 */
public class VespaFlavor {

    private final String name;
    private final NodeResources realResources, advertisedResources;

    public VespaFlavor(String name,
                       double advertisedVcpu,
                       double realVcpu,
                       double advertisedMemoryGb,
                       double realMemoryGb,
                       double diskGb,
                       double bandwidthGbps,
                       NodeResources.DiskSpeed diskSpeed,
                       NodeResources.StorageType storageType,
                       NodeResources.Architecture architecture) {
        this(name, advertisedVcpu, realVcpu, advertisedMemoryGb, realMemoryGb, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, NodeResources.GpuResources.zero());
    }

    public VespaFlavor(String name,
                       double advertisedVcpu,
                       double realVcpu,
                       double advertisedMemoryGb,
                       double realMemoryGb,
                       double diskGb,
                       double bandwidthGbps,
                       NodeResources.DiskSpeed diskSpeed,
                       NodeResources.StorageType storageType,
                       NodeResources.Architecture architecture,
                       NodeResources.GpuResources gpuResources) {
        this.name = name;
        this.realResources = new NodeResources(realVcpu, realMemoryGb, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, gpuResources);
        this.advertisedResources = new NodeResources(advertisedVcpu, advertisedMemoryGb, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, gpuResources);
    }

    public String name() { return name; }

    public NodeResources realResources() { return realResources; }

    public NodeResources advertisedResources() { return advertisedResources; }

    @Override
    public String toString() { return "flavor " + name; }

}
