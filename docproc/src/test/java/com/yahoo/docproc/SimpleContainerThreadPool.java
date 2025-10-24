// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.container.handler.threadpool.ContainerThreadPool;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Simple {@link ContainerThreadPool} for testing.
 *
 * @author johsol
 */
public class SimpleContainerThreadPool implements ContainerThreadPool {

    private final Executor executor = Executors.newCachedThreadPool();

    @Override public Executor executor() { return executor; }

}
