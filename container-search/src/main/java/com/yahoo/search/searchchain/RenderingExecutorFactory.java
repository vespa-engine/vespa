package com.yahoo.search.searchchain;

import com.yahoo.concurrent.ThreadFactoryFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Factory of the executor passed to renderers by default.
 *
 * @author bratseth
 */
class RenderingExecutorFactory {

    private final int maxQueuedRenderingTasksPerProcessor;
    private final int availableProcessors;

    public RenderingExecutorFactory() {
        this.maxQueuedRenderingTasksPerProcessor = 500;
        this.availableProcessors = Runtime.getRuntime().availableProcessors();
    }

    ThreadPoolExecutor createExecutor() {
        int maxOutstandingTasks = maxQueuedRenderingTasksPerProcessor * availableProcessors;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(availableProcessors, availableProcessors, 1L, TimeUnit.SECONDS,
                                                             new LinkedBlockingQueue<>(maxOutstandingTasks),
                                                             ThreadFactoryFactory.getThreadFactory("common-rendering"),
                                                             (task, exec) -> renderingRejected(maxOutstandingTasks));
        executor.prestartAllCoreThreads();
        return executor;
    }

    private void renderingRejected(int maxOutstandingTasks) {
        throw new RejectedExecutionException("More than " + maxOutstandingTasks + " rendering tasks queued, rejecting this");
    }

}
