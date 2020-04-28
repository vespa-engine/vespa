// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

/**
 * This is used when the target of an allocation search is to come as close as possible to the current allocation
 *
 * @author bratseth
 */
public class AllocationBasedResourceTarget extends ResourceTarget {

    private final AllocatableClusterResources current;

    public AllocationBasedResourceTarget(AllocatableClusterResources current) {
        super();
        this.current = current;
    }

    @Override
    public double clusterCpu() {
        return current.toAdvertisedClusterResources().nodeResources().vcpu() * current.nodes();
    }

    @Override
    public double groupMemory() {
        return current.toAdvertisedClusterResources().nodeResources().memoryGb() * current.groupSize();
    }

    @Override
    public double groupDisk() {
        return current.toAdvertisedClusterResources().nodeResources().diskGb() * current.groupSize();
    }

    @Override
    public double nodeMemory() {
        return current.toAdvertisedClusterResources().nodeResources().memoryGb();
    }

    @Override
    public double nodeDisk() {
        return current.toAdvertisedClusterResources().nodeResources().diskGb();
    }

}
