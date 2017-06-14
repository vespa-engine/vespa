// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.result;

import com.yahoo.persistence.spi.PartitionState;

import java.util.List;

/**
 * A result class for getPartitionState() requests.
 */
public class PartitionStateListResult extends Result {
    List<PartitionState> partitionStates = null;

    /**
     * Creates a result with an error.
     *
     * @param type The type of error
     * @param message A human-readable error message to further detail the error.
     */
    public PartitionStateListResult(Result.ErrorType type, String message) {
        super(type, message);
    }

    /**
     * Creates a result containing a list of all the partitions this provider has,
     * and their states.
     *
     * @param partitions A map containing all the partitions
     */
    public PartitionStateListResult(List<PartitionState> partitions) {
        this.partitionStates = partitions;
    }

    public List<PartitionState> getPartitionStates() {
        return partitionStates;
    }
}
