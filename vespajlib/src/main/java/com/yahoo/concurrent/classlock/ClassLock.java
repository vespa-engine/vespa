package com.yahoo.concurrent.classlock;

/**
 * @author valerijf
 */
public class ClassLock implements AutoCloseable {
    private final Class<?> clazz;
    private final ClassLocking classLocking;

    ClassLock(ClassLocking classLocking, Class<?> clazz) {
        this.classLocking = classLocking;
        this.clazz = clazz;
    }

    @Override
    public void close() {
        classLocking.unlock(clazz, this);
    }
}
