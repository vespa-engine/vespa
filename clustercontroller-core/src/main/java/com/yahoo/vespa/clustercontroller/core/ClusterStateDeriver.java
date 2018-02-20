// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

/**
 * Bucket-space aware transformation factory to "derive" new cluster states from an
 * existing state.
 */
public interface ClusterStateDeriver {
    /**
     * @param state Baseline cluster state used as a source for deriving a new state.
     *              MUST NOT be modified explicitly or implicitly.
     * @param bucketSpace The name of the bucket space for which the state should be derived
     * @return A cluster state instance representing the derived state, or <em>state</em> unchanged.
     */
    ClusterState derivedFrom(ClusterState state, String bucketSpace);
}
