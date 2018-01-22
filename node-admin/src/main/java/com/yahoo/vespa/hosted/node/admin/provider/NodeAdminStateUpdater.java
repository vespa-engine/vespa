// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.provider;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import java.util.Map;

public interface NodeAdminStateUpdater {
    enum State { TRANSITIONING, RESUMED, SUSPENDED_NODE_ADMIN, SUSPENDED}

    /**
     * Set the wanted state, and return whether the current state equals it.
     * Typically, this method should be called repeatedly until current state
     * has converged.
     */
    boolean setResumeStateAndCheckIfResumed(State wantedState);

    Map<String, Object> getDebugPage();
}
