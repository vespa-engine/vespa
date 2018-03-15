// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.response;

/**
 * Interface to get the published distribution state.
 */
public interface DistributionStates {

    DistributionState getPublishedState();
}
