// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

public abstract class ResourceTarget {

    /** The current allocation, leading to this target */
    private final AllocatableClusterResources current;

    public ResourceTarget(AllocatableClusterResources current) {
        this.current = current;
    }

    /** Returns the target total cpu to allocate to the entire cluster */
    public double clusterCpu() {
        return nodeCpu() * current().nodes();
    }

    /** Returns the target total memory to allocate to each group */
    public abstract double groupMemory();

    /** Returns the target total disk to allocate to each group */
    public abstract double groupDisk();

    /** Returns the target cpu to allocate to each node */
    public abstract double nodeCpu();

    /** Returns the target memory to allocate to each node */
    public abstract double nodeMemory();

    /** Returns the target disk to allocate to each node */
    public abstract double nodeDisk();

    protected AllocatableClusterResources current() { return current; }

}
