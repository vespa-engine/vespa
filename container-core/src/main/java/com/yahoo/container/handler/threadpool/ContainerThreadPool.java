// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.threadpool;

import java.util.concurrent.Executor;

/**
 * A configurable thread pool. This provides the worker threads used for normal request processing.
 *
 * @author bjorncs
 */
public interface ContainerThreadPool extends AutoCloseable {

    Executor executor();

    default void close() {}

}
