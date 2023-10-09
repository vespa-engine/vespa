// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.util.concurrent.locks.ReentrantLock;

/** 
 * An acquired lock which is released on close
 * 
 * @author bratseth
 */
public final class Lock implements AutoCloseable {

    private final ReentrantLock wrappedLock;

    Lock(ReentrantLock wrappedLock) {
        this.wrappedLock = wrappedLock;
    }

    /** Releases this lock */
    public void close() {
        wrappedLock.unlock();
    }

}
