// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

class ClusterStateUtil {

    static ClusterState emptyState() {
        return stateFromString("");
    }

    /**
     * Parse a given cluster state string into a returned ClusterState instance, wrapping any
     * parse exceptions in a RuntimeException.
     */
    static ClusterState stateFromString(final String stateStr) {
        try {
            return new ClusterState(stateStr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
