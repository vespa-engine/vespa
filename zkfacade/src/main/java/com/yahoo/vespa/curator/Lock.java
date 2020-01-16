// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.transaction.Mutex;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A cluster-wide re-entrant mutex which is released on (the last symmetric) close
 *
 * @author bratseth
 */
public class Lock implements Mutex {

    private final ReentrantLock lock;
    private final InterProcessLock mutex;
    private final String lockPath;

    public Lock(String lockPath, Curator curator) {
        this.lockPath = lockPath;
        this.lock = new ReentrantLock(true);
        mutex = curator.createMutex(lockPath);
    }

    /** Take the lock with the given timeout. This may be called multiple times from the same thread - each matched by a close */
    public void acquire(Duration timeout) throws UncheckedTimeoutException {
        boolean acquired;
        try {
            lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS);
            acquired = mutex.acquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (Exception e) {
            if (lock.isHeldByCurrentThread()) lock.unlock();
            throw new RuntimeException("Exception acquiring lock '" + lockPath + "'", e);
        }

        if ( ! acquired) {
            if (lock.isHeldByCurrentThread()) lock.unlock();
            throw new UncheckedTimeoutException("Timed out after waiting " + timeout +
                                                " to acquire lock '" + lockPath + "'");
        }
    }

    @Override
    public void close() {
        try {
            mutex.release();
            lock.unlock();
        }
        catch (Exception e) {
            throw new RuntimeException("Exception releasing lock '" + lockPath + "'");
        }
    }

}


