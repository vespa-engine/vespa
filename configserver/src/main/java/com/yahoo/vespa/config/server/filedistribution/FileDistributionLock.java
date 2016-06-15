// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.recipes.CuratorLock;
import com.yahoo.vespa.curator.recipes.CuratorLockException;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Global filedistribution lock to ensure only one configserver may work on filedistribution.
 * The implementation uses a combination of a {@link java.util.concurrent.locks.ReentrantLock} and
 * a {@link CuratorLock} to ensure both mutual exclusion within the JVM and
 * across JVMs via ZooKeeper.
 *
 * @author lulf
 */
public class FileDistributionLock implements Lock {
    private final Lock processLock;
    private final CuratorLock curatorLock;

    public FileDistributionLock(Curator curator, String zkPath) {
        this.processLock = new ReentrantLock();
        this.curatorLock = new CuratorLock(curator, zkPath);
    }

    @Override
    public void lock() {
        processLock.lock();
        try {
            curatorLock.lock();
        } catch (CuratorLockException e) {
            processLock.unlock();
            throw e;
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException();

    }

    @Override
    public boolean tryLock() {
        if (processLock.tryLock()) {
            if (curatorLock.tryLock()) {
                return true;
            } else {
                processLock.unlock();
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        TimeoutBudget budget = new TimeoutBudget(Clock.systemUTC(), Duration.ofMillis(unit.toMillis(timeout)));
        if (processLock.tryLock(budget.timeLeft().toMillis(), TimeUnit.MILLISECONDS)) {
            if (curatorLock.tryLock(budget.timeLeft().toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            } else {
                processLock.unlock();
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public void unlock() {
        try {
            curatorLock.unlock();
        } finally {
            processLock.unlock();
        }
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}

