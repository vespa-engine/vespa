// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.classlock;

/**
 * An acquired lock which is released on close
 *
 * @author valerijf
 */
public class ClassLock implements AutoCloseable {

    private final Class<?> clazz;
    private final ClassLocking classLocking;

    ClassLock(ClassLocking classLocking, Class<?> clazz) {
        this.classLocking = classLocking;
        this.clazz = clazz;
    }

    /**
     * Releases this lock
     *
     * @throws IllegalArgumentException if this lock has already been released
     */
    @Override
    public void close() {
        classLocking.unlock(clazz, this);
    }

}
