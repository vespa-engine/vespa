package com.yahoo.vespa.hosted.controller.concurrent;

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
