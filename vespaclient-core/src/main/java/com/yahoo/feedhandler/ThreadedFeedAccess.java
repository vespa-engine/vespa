// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.feedapi.SimpleFeedAccess;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class ThreadedFeedAccess implements SimpleFeedAccess {

    private final SimpleFeedAccess simpleFeedAccess;
    private final ExecutorService executorService;
    private final Executor executor;
    ThreadedFeedAccess(int numThreads, SimpleFeedAccess simpleFeedAccess) {
        this.simpleFeedAccess = simpleFeedAccess;
        if (numThreads <= 0) {
            numThreads = Runtime.getRuntime().availableProcessors();
        }
        if (numThreads > 1) {
            executorService = new ThreadPoolExecutor(numThreads, numThreads, 0L, TimeUnit.SECONDS,
                    new SynchronousQueue<>(false),
                    ThreadFactoryFactory.getDaemonThreadFactory("feeder"),
                    new ThreadPoolExecutor.CallerRunsPolicy());
            executor = executorService;
        } else {
            executorService = null;
            executor = new Executor() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }
            };
        }
    }

    @Override
    public void put(DocumentPut doc) {
        executor.execute(() -> simpleFeedAccess.put(doc));
    }

    @Override
    public void remove(DocumentRemove remove) {
        executor.execute(() -> simpleFeedAccess.remove(remove));
    }

    @Override
    public void update(DocumentUpdate update) {
        executor.execute(() -> simpleFeedAccess.update(update));
    }

    @Override
    public boolean isAborted() {
        return simpleFeedAccess.isAborted();
    }
    @Override
    public void close() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
