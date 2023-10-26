// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.recipes;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;

/**
 * A distributed atomic counter.
 *
 * @author Ulf Lilleengen
 */
public class CuratorCounter {

    private final DistributedAtomicLong counter;
    private final Path counterPath;

    public CuratorCounter(Curator curator, Path counterPath) {
        this.counter = curator.createAtomicCounter(counterPath.getAbsolute());
        this.counterPath = counterPath;
    }

    /** Convenience method for {@link #add(long)} with 1 */
    public long next() {
        return add(1L);
    }

    /** Convenience method for {@link #add(long)} with -1 */
    public long previous() {
        return add(-1L);
    }

    /**
     * Atomically add and return resulting value.
     *
     * @param delta value to add, may be negative
     * @return the resulting value
     * @throws IllegalStateException if addition fails
     */
    public synchronized long add(long delta) {
        try {
            AtomicValue<Long> value = counter.add(delta);
            if (!value.succeeded()) {
                throw new IllegalStateException("Add did not succeed");
            }
            return value.postValue();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to add value", e);
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
