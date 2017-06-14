// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi;

/**
 * Class that allows a provider to figure out if the node is currently up, the cluster is up and/or a
 * given bucket should be "ready" given the state.
 */
public interface ClusterState {
    /**
     * Returns true if the system has been set up to have
     * "ready" nodes, and the given bucket is in the ideal state
     * for readiness.
     *
     * @param bucket The bucket to check.
     * @return Returns true if the bucket should be set to "ready".
     */
    public boolean shouldBeReady(Bucket bucket);

    /**
     * @return Returns false if the cluster has been deemed down. This can happen
     * if the fleet controller has detected that too many nodes are down
     * compared to the complete list of nodes, and deigns the system to be
     * unusable.
     */
    public boolean clusterUp();

    /**
     * @return Returns false if this node has been set in a state where it should not
     * receive external load.
     */
    public boolean nodeUp();
}
