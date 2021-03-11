package com.yahoo.config.subscription.impl;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.TimingValues;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class JRTManagedConnectionPools {
    private static class JRTSourceThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread t = new Thread(runnable, String.format("jrt-config-requester-%d", System.currentTimeMillis()));
            // We want a daemon thread to avoid hanging threads in case something goes wrong in the config system
            t.setDaemon(true);
            return t;
        }
    }
    private static class CountedPool {
        final JRTConnectionPool pool;
        final ScheduledThreadPoolExecutor scheduler;
        long count;
        CountedPool(JRTConnectionPool requester) {
            pool = requester;
            scheduler = new ScheduledThreadPoolExecutor(1, new JRTSourceThreadFactory());
            count = 0;
            scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }
    }

    private final Map<ConfigSourceSet, CountedPool> pools = new HashMap<>();

    public JRTConfigRequester acquire(ConfigSourceSet sourceSet, TimingValues timingValues) {
        CountedPool countedPool;
        synchronized (pools) {
            countedPool = pools.get(sourceSet);
            if (countedPool == null) {
                countedPool = new CountedPool(new JRTConnectionPool(sourceSet));
                pools.put(sourceSet, countedPool);
            }
            countedPool.count++;
        }
        return new JRTConfigRequester(sourceSet, countedPool.scheduler, countedPool.pool, timingValues);
    }

    public synchronized void release(ConfigSourceSet sourceSet) {
        CountedPool countedPool;
        synchronized (pools) {
            countedPool = pools.get(sourceSet);
            if (countedPool != null)
                countedPool.count--;
            if (countedPool == null || countedPool.count > 0) return;
            pools.remove(sourceSet);
        }

        countedPool.pool.close();
        countedPool.scheduler.shutdownNow();
        try {
            countedPool.scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed shutting down scheduler:", e);
        }
    }
}
