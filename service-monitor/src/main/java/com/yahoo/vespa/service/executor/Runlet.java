// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.executor;

/**
 * A {@code Runlet} joins {@link AutoCloseable} with {@link Runnable} with the following semantics:
 *
 * <ul>
 *     <li>The {@link #run()} method may be called any number of times, followed by a single call to {@link #close()}.
 *     <li>The caller must ensure the calls are ordered by {@code happens-before}, i.e. the class can be thread-unsafe.
 * </ul>
 *
 * @author hakonhall
 */
public interface Runlet extends AutoCloseable, Runnable {
    void run();

    @Override
    void close();
}
