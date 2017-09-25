package com.yahoo.concurrent.classlock;

import java.util.HashMap;
import java.util.Map;

/**
 * @author valerijf
 */
public class ClassLocking {
    private final Map<Class<?>, ClassLock> classLocks = new HashMap<>();
    private final Object monitor = new Object();

    public ClassLock lock(Class<?> clazz) {
        synchronized (monitor) {
            while(classLocks.containsKey(clazz)) {
                try {
                    monitor.wait();
                } catch (InterruptedException ignored) { }
            }

            ClassLock classLock = new ClassLock(this, clazz);
            classLocks.put(clazz, classLock);
            return classLock;
        }
    }

    void unlock(Class<?> clazz, ClassLock classLock) {
        synchronized (monitor) {
            if (classLock.equals(classLocks.get(clazz))) {
                classLocks.remove(clazz);
                monitor.notifyAll();
            } else {
                throw new IllegalArgumentException("Lock has already been released");
            }
        }
    }
}
