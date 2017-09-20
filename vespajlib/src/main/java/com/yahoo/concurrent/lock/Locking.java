package com.yahoo.concurrent.lock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author valerijf
 */
public class Locking {
    private final Map<Class<?>, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Locks class. This will block until the lock is acquired.
     * Users of this <b>must</b> close any lock acquired.
     *
     * @param key the key to lock
     * @return the acquired lock
     */
    public Lock lock(Class<?> key) {
        try {
            ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock(true));
            lock.tryLock(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            return new Lock(lock);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for lock of " + key);
        }
    }
}
