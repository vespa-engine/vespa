// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.recipes;

import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;

/**
 * A distributed atomic counter.
 *
 * @author lulf
 * @since 5.1
 */
public class CuratorCounter {

    private final DistributedAtomicLong counter;

    public CuratorCounter(Curator curator, String counterPath) {
        this.counter = curator.createAtomicCounter(counterPath);
    }

    /**
     * Atomically increment and return next value.
     *
     * @return the new value.
     */
    public synchronized long next() {
        try {
            AtomicValue<Long> value = counter.increment();
            if (!value.succeeded()) {
                throw new RuntimeException("Increment did not succeed");
            }
            return value.postValue();
        } catch (Exception e) {
            throw new RuntimeException("Unable to get next value", e);
        }
    }

    public synchronized void set(long current) {
        try {
            counter.trySet(current);
        } catch (Exception e) {
            throw new RuntimeException("Unable to set value", e);
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
            throw new RuntimeException("Error initializing atomic counter", e);
        }
    }

}
