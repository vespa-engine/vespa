package com.yahoo.concurrent;

import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds a map of locks indexed on keys of a given type.
 * This is suitable in cases where exclusive access should be granted to any one of a set of keyed objects and
 * there is a finite collection of keyed objects.
 * 
 * The returned locks are reentrant (i.e the owning thread may call lock multiple times) and auto-closable.
 * 
 * Typical use is
 * <code>
 *     try (Lock lock = locks.lock(id)) {
 *         exclusive use of the object with key id
 *     }
 * </code>
 * 
 * @author bratseth
 */
public class Locks<TYPE> {

    private final Map<TYPE, ReentrantLock> locks = new ConcurrentHashMap<>();
    
    private final long timeoutMs;
    
    public Locks(int timeout, TimeUnit timeoutUnit) {
        timeoutMs = timeoutUnit.toMillis(timeout);
    }

    /**
     * Locks key. This will block until the key is acquired.
     * Users of this <b>must</b> close any lock acquired.
     * 
     * @param key the key to lock
     * @return the acquired lock
     * @throws UncheckedTimeoutException if the lock could not be acquired within the timeout
     */
    public Lock lock(TYPE key) {
        try {
            ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock(true));
            boolean acquired = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
            if ( ! acquired)
                throw new UncheckedTimeoutException("Timed out waiting for the lock to " + key);
            return new Lock(lock);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for lock of " + key);
        }
    }

}
