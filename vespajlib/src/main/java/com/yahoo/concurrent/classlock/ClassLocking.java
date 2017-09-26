package com.yahoo.concurrent.classlock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * @author valerijf
 */
public class ClassLocking {
    private final Map<String, ClassLock> classLocks = new HashMap<>();
    private final Object monitor = new Object();

    public ClassLock lock(Class<?> clazz) {
        return lockWhile(clazz, () -> true);
    }

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

    public void interrupt() {
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }
}
