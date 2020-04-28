// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

public abstract class ResourceTarget {

    private final int sourceNodes;
    private final int sourceGroups;

    public ResourceTarget(int sourceNodes, int sourceGroups) {
        this.sourceNodes = sourceNodes;
        this.sourceGroups = sourceGroups;
    }

    /** Returns the number of nodes of the *source* allocation causing this target */
    public int sourceNodes() { return sourceNodes; }

    /** Returns the number of groups of the *source* allocation causing this target */
    public int sourceGroups() { return sourceGroups; }

    /** Returns the group size of the source allocation producing this target */
    public int sourceGroupSize() {
        // ceil: If the division does not produce a whole number we assume some node is missing
        return (int)Math.ceil((double)sourceNodes / sourceGroups);
    }

    /** Returns the target total cpu to allocate to the entire cluster */
    public abstract double clusterCpu();

    /** Returns the target total memory to allocate to each group */
    public abstract double groupMemory();

    /** Returns the target total disk to allocate to each group */
    public abstract double groupDisk();

    /** Returns the target memory to allocate to each node */
    public abstract double nodeMemory();

    /** Returns the target disk to allocate to each node */
    public abstract double nodeDisk();

}
