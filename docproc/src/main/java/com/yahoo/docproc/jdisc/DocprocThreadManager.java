// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.document.DocumentUtil;
import com.yahoo.log.LogLevel;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * @author Einar M R Rosenvinge
 */
class DocprocThreadManager {

    private static Logger log = Logger.getLogger(DocprocThreadManager.class.getName());

    private final long maxConcurrentByteSize;
    private final AtomicLong bytesStarted = new AtomicLong(0);
    private final AtomicLong bytesFinished = new AtomicLong(0);

    DocprocThreadManager(double maxConcurrentFactor, double documentExpansionFactor, int containerCoreMemoryMb) {
        this((long) (((double) DocumentUtil.calculateMaxPendingSize(maxConcurrentFactor, documentExpansionFactor,
                                                      containerCoreMemoryMb)) * maxConcurrentFactor));
    }

    DocprocThreadManager(long maxConcurrentByteSize) {
        final int MINCONCURRENTBYTES=256*1024*1024;    //256M
        if (maxConcurrentByteSize < MINCONCURRENTBYTES) {
            maxConcurrentByteSize = MINCONCURRENTBYTES;
        }

        this.maxConcurrentByteSize = maxConcurrentByteSize;
        log.log(LogLevel.CONFIG, "Docproc service allowed to concurrently process "
                               + (((double) maxConcurrentByteSize) / 1024.0d / 1024.0d) + " megabytes of input data.");
    }

    boolean isAboveLimit() {
        return (bytesFinished.get() - bytesStarted.get() > maxConcurrentByteSize);
    }
    void beforeExecute(DocumentProcessingTask task) {
        bytesStarted.getAndAdd(task.getApproxSize());
    }

    void afterExecute(DocumentProcessingTask task) {
        bytesFinished.getAndAdd(task.getApproxSize());
    }
    void shutdown() {
    }

}
