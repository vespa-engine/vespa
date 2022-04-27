// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.transaction.Mutex;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that holds two locks, originally used for transitioning from one lock to
 * another, where you need to hold both the old lock and the new lock in the
 * transition period. Locks are acquired in constructor.
 *
 * @author hmusum
 */
public class MultiplePathsLock implements Mutex {

    private static final Logger log = Logger.getLogger(MultiplePathsLock.class.getName());

    private final List<Lock> locks;

    /** Wrapped locks, in acquisition order. */
    public MultiplePathsLock(Lock... locks) {
        this.locks = List.of(locks);
    }

    @Override
    public void close() {
        close(0);
    }

    private void close(int i) {
        if (i < locks.size())
            try (Lock lock = locks.get(i)) {
                close(i + 1);
                log.log(Level.FINE, "Closing lock " + lock.lockPath());
            }
    }

}


