// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.recipes;

import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * @author lulf
 * @since 5.1
 */
public class CuratorLock implements Lock {

    private final InterProcessLock mutex;

    public CuratorLock(Curator curator, String lockPath) {
        this.mutex = curator.createMutex(lockPath);
    }

    public boolean hasLock() {
        return mutex.isAcquiredInThisProcess();
    }

    @Override
    public void lock() {
        try {
            mutex.acquire();
        } catch (Exception e) {
            throw new CuratorLockException(e);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock() {
        try {
            return tryLock(0, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new CuratorLockException(e);
        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        try {
            return mutex.acquire(time, unit);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new CuratorLockException(e);
        }
    }

    @Override
    public void unlock() {
        try {
            mutex.release();
        } catch (Exception e) {
            throw new CuratorLockException(e);
        }
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }


    @Override
    public String toString() {
        return mutex.toString();
    }
}
