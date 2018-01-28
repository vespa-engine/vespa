// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.provider;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public interface NodeAdminStateUpdater extends NodeAdminDebugHandler {
    enum State { TRANSITIONING, RESUMED, SUSPENDED_NODE_ADMIN, SUSPENDED}

    /**
     * Set the wanted state, and return whether the current state equals it.
     * Typically, this method should be called repeatedly until current state
     * has converged.
     */
    boolean setResumeStateAndCheckIfResumed(State wantedState);
}
