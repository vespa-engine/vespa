// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.recipes;

import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;

/**
 * A distributed atomic counter.
 *
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class CuratorCounter {

    private final DistributedAtomicLong counter;
    private final String counterPath;

    public CuratorCounter(Curator curator, String counterPath) {
        this.counter = curator.createAtomicCounter(counterPath);
        this.counterPath = counterPath;
    }

    /**
     * Atomically increment and return resulting value.
     *
     * @return the resulting value
     * @throws IllegalStateException if increment fails
     */
    public synchronized long next() {
        try {
            AtomicValue<Long> value = counter.increment();
            if (!value.succeeded()) {
                throw new IllegalStateException("Increment did not succeed");
            }
            return value.postValue();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to get next value", e);
        }
    }

    /**
     * Atomically decrement and return the resulting value.
     *
     * @return the resulting value
     * @throws IllegalStateException if decrement fails
     */
    public synchronized long previous() {
        try {
            AtomicValue<Long> value = counter.subtract(1L);
            if (!value.succeeded()) {
                throw new IllegalStateException("Decrement did not succeed");
            }
            return value.postValue();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to get previous value", e);
        }
    }

    public synchronized void set(long current) {
        try {
            counter.trySet(current);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to set value", e);
        }
    }

    public long get() {
        try {
            AtomicValue<Long> value = counter.get();
            if (!value.succeeded()) {
                throw new RuntimeException("Get did not succeed");
            }
            return value.postValue();
        } catch (Exception e) {
            throw new RuntimeException("Unable to get value", e);
        }
    }

    public void initialize(long value) {
        try {
            counter.initialize(value);
        } catch (Exception e) {
            throw new IllegalStateException("Error initializing atomic counter", e);
        }
    }
    
    @Override
    public String toString() {
        return "curator counter " + counterPath;
    }

}
