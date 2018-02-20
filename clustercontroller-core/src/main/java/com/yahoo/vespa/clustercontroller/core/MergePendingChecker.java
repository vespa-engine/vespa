// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Implementations provide functionality for checking if a particular bucket space
 * has merges reported as pending from the cluster's distributor nodes. It is up
 * to the implementation to determine the exact semantics of what "pending" implies.
 */
public interface MergePendingChecker {

    boolean hasMergesPending(String bucketSpace, int contentNodeIndex);

}
