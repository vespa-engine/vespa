// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.classlock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * This class is injectable to Vespa plugins and is used to acquire locks cross
 * application deployments.
 *
 * @author valerijf
 */
public class ClassLocking {

    private final Map<String, ClassLock> classLocks = new HashMap<>();
    private final Object monitor = new Object();

    /**
     * Locks key. This will block until the key is acquired.
     * Users of this <b>must</b> close any lock acquired.
     */
    public ClassLock lock(Class<?> clazz) {
        return lockWhile(clazz, () -> true);
    }

    /**
     * Locks key. This will block until the key is acquired or the interrupt condition is
     * no longer true. Condition is only checked at the start, everytime a lock is released
     * and when {@link #interrupt()} is called.
     *
     * Users of this <b>must</b> close any lock acquired.
     *
     * @throws LockInterruptException if interruptCondition returned false before
     * the lock could be acquired
     */
    public ClassLock lockWhile(Class<?> clazz, BooleanSupplier interruptCondition) {
        synchronized (monitor) {
            while (classLocks.containsKey(clazz.getName())) {
                try {
                    monitor.wait();
                } catch (InterruptedException ignored) {
                }

                if (!interruptCondition.getAsBoolean()) {
                    throw new LockInterruptException();
                }
            }

            ClassLock classLock = new ClassLock(this, clazz);
            classLocks.put(clazz.getName(), classLock);
            return classLock;
        }
    }

    void unlock(Class<?> clazz, ClassLock classLock) {
        synchronized (monitor) {
            if (classLock.equals(classLocks.get(clazz.getName()))) {
                classLocks.remove(clazz.getName());
                monitor.notifyAll();
            } else {
                throw new IllegalArgumentException("Lock has already been released");
            }
        }
    }

    /**
     * Notifies {@link #lockWhile} to check the interrupt condition
     */
    public void interrupt() {
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

}
