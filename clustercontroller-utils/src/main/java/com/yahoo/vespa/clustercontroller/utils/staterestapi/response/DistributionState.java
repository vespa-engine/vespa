// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.response;

import java.util.Map;

/**
 * A distribution state representing the baseline cluster state and per bucket space states.
 */
public class DistributionState {
    private final String baselineState;
    private final Map<String, String> bucketSpaceStates;

    public DistributionState(String baselineState,
                             Map<String, String> bucketSpaceStates) {
        this.baselineState = baselineState;
        this.bucketSpaceStates = bucketSpaceStates;
    }

    public String getBaselineState() {
        return baselineState;
    }

    public Map<String, String> getBucketSpaceStates() {
        return bucketSpaceStates;
    }
}
