// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;


import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.api.FeedClientImpl;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * Factory for creating FeedClient.
 *
 * @author dybis
 */
public class FeedClientFactory {

    /**
     * Creates a FeedClient. Call this sparingly: Feed clients are expensive and should be as long-lived as possible.
     *
     * @param sessionParams parameters for connection, hosts, cluster configurations and more.
     * @param resultCallback on each result, this callback is called.
     * @return newly created FeedClient API object.
     */
    public static FeedClient create(SessionParams sessionParams, FeedClient.ResultCallback resultCallback) {
        return new FeedClientImpl(sessionParams, resultCallback, createTimeoutExecutor(), Clock.systemUTC());
    }

    static ScheduledThreadPoolExecutor createTimeoutExecutor() {
        ScheduledThreadPoolExecutor timeoutExecutor;
        timeoutExecutor = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("timeout-"));
        timeoutExecutor.setRemoveOnCancelPolicy(true);
        timeoutExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        timeoutExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return timeoutExecutor;
    }

    private static class DaemonThreadFactory implements ThreadFactory {

        private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
        private final String prefix;

        private DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread t = defaultThreadFactory.newThread(runnable);
            t.setDaemon(true);
            t.setName(prefix + t.getName());
            return t;
        }
    }

}
