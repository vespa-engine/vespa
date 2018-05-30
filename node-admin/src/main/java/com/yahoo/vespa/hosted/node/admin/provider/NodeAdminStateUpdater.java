// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.provider;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public interface NodeAdminStateUpdater extends NodeAdminDebugHandler {
    enum State { TRANSITIONING, RESUMED, SUSPENDED_NODE_ADMIN, SUSPENDED}

    /**
     * Set the wanted state, and assert whether the current state equals it.
     * Typically, this method should be called repeatedly until current state
     * has converged.
     *
     * @throws RuntimeException (or a subclass) if the state has not converged yet.
     */
    void setResumeStateAndCheckIfResumed(State wantedState);
}
