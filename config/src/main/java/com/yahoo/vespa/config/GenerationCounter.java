// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

/**
 * Interface for counters.
 *
 * @author Ulf Lilleengen
 */
public interface GenerationCounter {
    /**
     * Increment counter and return new value.
     *
     * @return incremented counter value.
     */
    long increment();

    /**
     * @return current counter value.
     */
    long get();
}
