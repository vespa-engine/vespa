// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

/**
 * Represents the row id and partition id of a search interface node.
 *
 * @author <a href="mailto:geirst@yahoo-inc.com">Geir Storli</a>
 */
public class NodeSpec {

    private final int rowId;
    private final int partitionId;

    public NodeSpec(int rowId, int partitionId) {
        if (rowId < 0) {
            throw new IllegalArgumentException("RowId(" + rowId + ") can not be below 0");
        }
        if (partitionId < 0) {
            throw new IllegalArgumentException("PartId(" + partitionId + ") can not be below 0");
        }
        this.rowId = rowId;
        this.partitionId = partitionId;
    }

    public int rowId() {
        return rowId;
    }

    public int partitionId() {
        return partitionId;
    }

}
