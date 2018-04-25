// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.listeners;

import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;

public interface SystemStateListener {

    // TODO consider rename to bundle
    void handleNewPublishedState(ClusterStateBundle states);

    default void handleNewCandidateState(ClusterStateBundle states) {}

}
