// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.transaction.Mutex;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A cluster-wide reentrant mutex which is released on (the last symmetric) close
 *
 * @author bratseth
 */
public class CuratorMutex implements Mutex {

    private final InterProcessMutex mutex;
    private final String lockPath;

    public CuratorMutex(String lockPath, CuratorFramework curator) {
        this.lockPath = lockPath;
        mutex = new InterProcessMutex(curator, lockPath);
    }

    /** Take the lock with the given timeout. This may be called multiple times from the same thread - each matched by a close */
    public void acquire(Duration timeout) {
        boolean acquired;
        try {
            acquired = mutex.acquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (Exception e) {
            throw new RuntimeException("Exception acquiring lock '" + lockPath + "'", e);
        }

        if (! acquired) throw new UncheckedTimeoutException("Timed out after waiting " + timeout.toString() +
                                                            " to acquire lock + '" + lockPath + "'");
    }

    @Override
    public void close() {
        try {
            mutex.release();
        }
        catch (Exception e) {
            throw new RuntimeException("Exception releasing lock '" + lockPath + "'");
        }
    }

}


