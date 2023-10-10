// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

/**
 * Represents a region of execution, e.g. the block of a try-with-resources.
 *
 * @author hakonhall
 */
@FunctionalInterface
public interface CriticalRegion extends AutoCloseable {
    /**
     * Ends the critical region.
     *
     * @throws IllegalStateException if the current thread is different from the one that started
     *         the critical region.
     */
    @Override void close() throws IllegalStateException;
}
