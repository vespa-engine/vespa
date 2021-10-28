// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

/**
 * Represents the group and partition id of a search interface node.
 *
 * @author geirst
 */
public class NodeSpec {

    private final int groupIndex;
    private final int partitionId;

    public NodeSpec(int groupIndex, int partitionId) {
        if (groupIndex < 0) {
            throw new IllegalArgumentException("GroupId(" + groupIndex + ") can not be below 0");
        }
        if (partitionId < 0) {
            throw new IllegalArgumentException("PartId(" + partitionId + ") can not be below 0");
        }
        this.groupIndex = groupIndex;
        this.partitionId = partitionId;
    }

    /** 
     * Returns an index of the group of this node. 
     * This is a 0-base continuous integer id, not necessarily the same as the group id assigned by the user
     * or node repo.
     * This index is called a "row id" in some places in Vespa for historical reasons.
     */
    public int groupIndex() {
        return groupIndex;
    }

    /**
     * Returns the partition id of this, which is also a contiguous integer id, not necessarily
     * the same as the group id assigned by the user or node repo.
     */
    public int partitionId() {
        return partitionId;
    }

}
