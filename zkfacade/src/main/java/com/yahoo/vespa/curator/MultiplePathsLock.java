// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.path.Path;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that holds two locks, originally used for transitioning from one lock to
 * another, where you need to hold both the old lock and the new lock in the
 * transition period. Locks are acquired in constructor.
 *
 * @author hmusum
 */
public class MultiplePathsLock extends Lock {

    private static final Logger log = Logger.getLogger(MultiplePathsLock.class.getName());

    private final Lock oldLock;

    public MultiplePathsLock(Path newLockPath, Path oldLockPath, Duration timeout, Curator curator) {
        super(newLockPath.getAbsolute(), curator);
        log.log(Level.INFO, "Acquiring lock " + oldLockPath);
        this.oldLock = curator.lock(oldLockPath, timeout);;
        log.log(Level.INFO, "Acquiring lock " + lockPath());
        super.acquire(timeout);
    }

    @Override
    public void close() {
        log.log(Level.INFO, "Closing lock " + oldLock.lockPath());
        oldLock.close();
        log.log(Level.INFO, "Closing lock " + lockPath());
        super.close();
    }

}


