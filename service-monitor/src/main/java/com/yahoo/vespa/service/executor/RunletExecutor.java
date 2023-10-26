// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.executor;

import java.time.Duration;

/**
 * @author hakonhall
 */
public interface RunletExecutor extends AutoCloseable {
    /**
     * Execute the task periodically with a fixed delay.
     *
     * <p>If the execution is {@link Cancellable#cancel() cancelled}, the runlet is {@link Runlet#close() closed}
     * as soon as possible.
     */
    Cancellable scheduleWithFixedDelay(Runlet runlet, Duration delay);

    /** Shuts down and waits for all execution to wind down. */
    @Override
    void close();
}
