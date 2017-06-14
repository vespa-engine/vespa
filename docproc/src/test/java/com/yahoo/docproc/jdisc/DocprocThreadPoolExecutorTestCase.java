// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocprocThreadPoolExecutorTestCase {
    private final Set<Long> threadIds = Collections.synchronizedSet(new HashSet<Long>());

    @Test
    public void threadPool() throws InterruptedException {
        int numThreads = 8;
        int numTasks = 200;

        LinkedBlockingQueue<Runnable> q = new LinkedBlockingQueue<>();
        DocprocThreadManager mgr = new DocprocThreadManager(1000l);
        DocprocThreadPoolExecutor pool = new DocprocThreadPoolExecutor(numThreads, q, mgr);

        List<MockedDocumentProcessingTask> tasks = new ArrayList<>(numTasks);
        for (int i = 0; i < numTasks; i++) {
            tasks.add(new MockedDocumentProcessingTask());
        }

        for (int i = 0; i < numTasks; i++) {
            pool.execute(tasks.get(i));
        }
        pool.shutdown();
        pool.awaitTermination(120L, TimeUnit.SECONDS);

        for (int i = 0; i < numTasks; i++) {
            assertTrue(tasks.get(i).hasBeenRun());
        }

        System.err.println(threadIds);
        assertEquals(numThreads, threadIds.size());
    }

    private class MockedDocumentProcessingTask extends DocumentProcessingTask {
        private boolean hasBeenRun = false;

        public MockedDocumentProcessingTask() {
            super(null, null, null);
        }

        @Override
        public void run() {
            threadIds.add(Thread.currentThread().getId());
            System.err.println(System.currentTimeMillis() + "   MOCK Thread " + Thread.currentThread().getId() + " running task " + this);
            for (int i = 0; i < 100000; i++) {
                Math.sin((double) (System.currentTimeMillis() / 10000L));
            }
            System.err.println(System.currentTimeMillis() + "   MOCK Thread " + Thread.currentThread().getId() + " DONE task " + this);
            hasBeenRun = true;
        }

        @Override
        public int getApproxSize() {
            return 333;
        }

        @Override
        public String toString() {
            return "seqNum " + getSeqNum();
        }

        public boolean hasBeenRun() {
            return hasBeenRun;
        }
    }
}
