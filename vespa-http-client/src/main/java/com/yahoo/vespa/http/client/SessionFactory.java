// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.SessionParams;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * Factory for creating {@link Session} instances.
 *
 * @author Einar M R Rosenvinge
 * @deprecated use either FeedClient or SyncFeedClient // TODO: Remove on Vespa 8
 */
@Deprecated
public final class SessionFactory {

    /**
     * Creates a {@link Session} with the given parameters.
     *
     * @param params the parameters to use when creating the Session.
     * @return a new Session instance
     */
    public static Session create(SessionParams params) {
        return createInternal(params);
    }

    @SuppressWarnings("deprecation")
    static Session createInternal(SessionParams params) {
        return new com.yahoo.vespa.http.client.core.api.SessionImpl(params, createTimeoutExecutor(), Clock.systemUTC());
    }

    static ScheduledThreadPoolExecutor createTimeoutExecutor() {
        ScheduledThreadPoolExecutor timeoutExecutor;
        timeoutExecutor = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("timeout-"));
        timeoutExecutor.setRemoveOnCancelPolicy(true);
        timeoutExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        timeoutExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return timeoutExecutor;
    }

    /**
     * Creates a {@link Session} to a single {@link Endpoint}, with default values for everything.
     * For full control of all parameters, or to feed to more than one Endpoint or more than one {@link Cluster},
     * see {@link #create(com.yahoo.vespa.http.client.config.SessionParams)}.
     *
     * @param endpoint the Endpoint to feed to.
     * @return a new Session instance
     * @see #create(com.yahoo.vespa.http.client.config.SessionParams)
     */
    public static Session create(Endpoint endpoint) {
        return createInternal(endpoint);
    }

    static Session createInternal(Endpoint endpoint) {
        SessionParams params = new SessionParams.Builder().addCluster(
                new Cluster.Builder().addEndpoint(endpoint).build()).build();
        return create(params);
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
        private final String prefix;

        public DaemonThreadFactory(String prefix) {
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
