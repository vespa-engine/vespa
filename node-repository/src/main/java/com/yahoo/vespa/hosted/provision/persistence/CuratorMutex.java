// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.transaction.Mutex;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    /** Take the lock. This may be called multiple times from the same thread - each matched by a close */
    public void acquire() {
        try {
            boolean acquired = mutex.acquire(60, TimeUnit.SECONDS);
            if ( ! acquired) {
                throw new TimeoutException("Timed out after waiting 60 seconds");
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Exception acquiring lock '" + lockPath + "'", e);
        }
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


