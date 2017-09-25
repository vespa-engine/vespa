package com.yahoo.concurrent.classlock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author valerijf
 */
public class ClassLocking {
    private final Map<Class<?>, ClassLock> classLocks = new HashMap<>();

    public synchronized ClassLock lock(Class<?> clazz) {
        return tryLock(clazz, () -> true);
    }

    public synchronized ClassLock tryLock(Class<?> clazz, Supplier<Boolean> continueRetrying) {
        while(classLocks.containsKey(clazz)) {
            try {
                wait();
            } catch (InterruptedException ignored) {
                if (! continueRetrying.get()) {
                    throw new LockInterruptException();
                }
            }
        }

        ClassLock classLock = new ClassLock(this, clazz);
        classLocks.put(clazz, classLock);
        return classLock;
    }

    synchronized void unlock(Class<?> clazz, ClassLock classLock) {
        if (classLock.equals(classLocks.get(clazz))) {
            classLocks.remove(clazz);
            notifyAll();
        } else {
            throw new IllegalArgumentException("Lock has already been released");
        }
    }
}
