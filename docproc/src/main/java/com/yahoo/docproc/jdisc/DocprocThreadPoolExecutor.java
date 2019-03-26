// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.log.LogLevel;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Einar M R Rosenvinge
 */
public class DocprocThreadPoolExecutor extends ThreadPoolExecutor {

    private static Logger log = Logger.getLogger(DocprocThreadPoolExecutor.class.getName());
    private DocprocThreadManager threadManager;

    public DocprocThreadPoolExecutor(int maxNumThreads, BlockingQueue<Runnable> queue, DocprocThreadManager threadMgr) {
        super((maxNumThreads > 0) ? maxNumThreads : Runtime.getRuntime().availableProcessors(),
              (maxNumThreads > 0) ? maxNumThreads : 8192,
              1, TimeUnit.SECONDS,
              queue,
              new DaemonThreadFactory("docproc-"));
        this.threadManager = threadMgr;
        allowCoreThreadTimeOut(false);
        log.log(LogLevel.DEBUG, "Created docproc thread pool with " + super.getCorePoolSize() + " worker threads.");
    }

    @Override
    protected void beforeExecute(Thread thread, Runnable runnable) {
        threadManager.beforeExecute((DocumentProcessingTask) runnable);
    }

    @Override
    protected void afterExecute(Runnable runnable, Throwable throwable) {
        threadManager.afterExecute((DocumentProcessingTask) runnable);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        threadManager.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> list =  super.shutdownNow();
        threadManager.shutdown();
        return list;
    }

    boolean isAboveLimit() {
        return threadManager.isAboveLimit();
    }

}
