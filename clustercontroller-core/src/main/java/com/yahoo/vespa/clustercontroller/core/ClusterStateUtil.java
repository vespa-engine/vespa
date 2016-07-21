// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

class ClusterStateUtil {

    static ClusterState emptyState() {
        try {
            return new ClusterState("");
        } catch (Exception e) {
            throw new RuntimeException(e); // Should never happen for empty state string
        }
    }

    static boolean structurallyEqual(final ClusterState lhs, final ClusterState rhs) {
        return false;
    }
}
