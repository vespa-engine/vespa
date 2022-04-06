// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.path.Path;

import java.time.Duration;

/**
 * Class that holds two locks, originally used for transitioning from one lock to
 * another, where you need to hold both the old lock and the new lock in the
 * transition period.
 *
 * @author hmusum
 */
public class MultiplePathsLock extends Lock {

    private final Lock oldLock;
    private final Lock newLock;

    public MultiplePathsLock(Path newLockPath, Path oldLockPath, Duration timeout, Curator curator) {
        super(newLockPath.getAbsolute(), curator);
        this.newLock = curator.lock(newLockPath, timeout);
        this.oldLock = curator.lock(oldLockPath, timeout);;
    }

    @Override
    public void acquire(Duration timeout) throws UncheckedTimeoutException {
        oldLock.acquire(timeout);
        newLock.acquire(timeout);
    }

    @Override
    public void close() {
        oldLock.close();
        newLock.close();
    }

}


