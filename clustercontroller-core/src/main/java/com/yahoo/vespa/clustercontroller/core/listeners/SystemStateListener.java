// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.listeners;

import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;

public interface SystemStateListener {

    // TODO consider rename to bundle
    void handleNewPublishedState(ClusterStateBundle states);

    /**
     * Invoked at the edge when all pending cluster state bundles and version activations
     * have been successfully ACKed by all distributors in the cluster.
     *
     * @param states bundle that has converged across all distributors
     */
    default void handleStateConvergedInCluster(ClusterStateBundle states) {}

    default void handleNewCandidateState(ClusterStateBundle states) {}

}
