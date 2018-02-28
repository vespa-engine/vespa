// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper functions for constructing a ClusterStateBundle for a baseline state and zero or more
 * explicit bucket space states.
 */
public class ClusterStateBundleUtil {

    public static ClusterStateBundle makeBundle(String baselineState, StateMapping... bucketSpaceStates) {
        return ClusterStateBundle.of(AnnotatedClusterState.withoutAnnotations(ClusterState.stateFromString(baselineState)),
                Stream.of(bucketSpaceStates).collect(Collectors.toMap(sm -> sm.bucketSpace, sm -> sm.state)));
    }

}
