// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;

/**
 * This is used when the target of an allocation search is to come as close as possible to the current allocation
 *
 * @author bratseth
 */
public class AllocationBasedResourceTarget extends ResourceTarget {

    private final AllocatableClusterResources resources;

    public AllocationBasedResourceTarget(AllocatableClusterResources resources) {
        super(resources.nodes(), resources.groups());
        this.resources = resources;
    }

    @Override
    public double clusterCpu() {
        return resources.toAdvertisedClusterResources().nodeResources().vcpu() * resources.nodes();
    }

    @Override
    public double groupMemory() {
        return resources.toAdvertisedClusterResources().nodeResources().memoryGb() * sourceGroupSize();
    }

    @Override
    public double groupDisk() {
        return resources.toAdvertisedClusterResources().nodeResources().diskGb() * sourceGroupSize();
    }

    @Override
    public double nodeMemory() {
        return resources.toAdvertisedClusterResources().nodeResources().memoryGb();
    }

    @Override
    public double nodeDisk() {
        return resources.toAdvertisedClusterResources().nodeResources().diskGb();
    }

}
