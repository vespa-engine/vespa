// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

/**
 * Tuple representing a mapping from a named bucket space to the derived ClusterState
 * for that space.
 */
public class StateMapping {

    final String bucketSpace;
    final ClusterState state;

    private StateMapping(String bucketSpace, ClusterState state) {
        this.bucketSpace = bucketSpace;
        this.state = state;
    }

    public static StateMapping of(String bucketSpace, String state) {
        return new StateMapping(bucketSpace, ClusterState.stateFromString(state));
    }

}
