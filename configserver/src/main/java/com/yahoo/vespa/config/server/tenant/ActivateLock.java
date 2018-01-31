// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.recipes.CuratorLock;

import java.util.concurrent.TimeUnit;

/**
 * A lock to protect session activation.
 *
 * @author lulf
 * @since 5.1
 */
public class ActivateLock {

    private static final String ACTIVATE_LOCK_NAME = "activateLock";
    private final CuratorLock curatorLock;

    public ActivateLock(Curator curator, Path rootPath) {
        this.curatorLock = new CuratorLock(curator, rootPath.append(ACTIVATE_LOCK_NAME).getAbsolute());
    }

    public boolean acquire(TimeoutBudget timeoutBudget, boolean ignoreLockError) {
        try {
            return curatorLock.tryLock(timeoutBudget.timeLeft().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (!ignoreLockError) {
                throw new RuntimeException(e);
            }
            return false;
        }
    }

    public void release() {
        if (curatorLock.hasLock()) {
            curatorLock.unlock();
        }
    }

    @Override
    public String toString() {
        return "ActivateLock (" + curatorLock + "), has lock: " + curatorLock.hasLock();
    }

}
