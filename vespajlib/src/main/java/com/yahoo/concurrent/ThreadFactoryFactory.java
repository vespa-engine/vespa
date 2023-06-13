// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author baldersheim
 */
public class ThreadFactoryFactory {

    static private final Map<String, PooledFactory> factory = new HashMap<>();

    static public synchronized ThreadFactory getThreadFactory(String name) {
        PooledFactory p = factory.get(name);
        if (p == null) {
            p = new PooledFactory(name);
            factory.put(name, p);
        }
        return p.getFactory(false);
    }

    static public synchronized ThreadFactory getDaemonThreadFactory(String name) {
        PooledFactory p = factory.get(name);
        if (p == null) {
            p = new PooledFactory(name);
            factory.put(name, p);
        }
        return p.getFactory(true);
    }

    private static class PooledFactory {

        private final String name;
        private final AtomicInteger poolId = new AtomicInteger(1);

        private static class Factory implements ThreadFactory {

            final ThreadGroup group;
            final AtomicInteger threadNumber = new AtomicInteger(1);
            final String namePrefix;
            final boolean isDaemon;

            @SuppressWarnings("removal")
            Factory(String name, boolean isDaemon) {
                this.isDaemon = isDaemon;
                SecurityManager s = System.getSecurityManager();
                group = (s != null)
                        ? s.getThreadGroup()
                        : Thread.currentThread().getThreadGroup();
                namePrefix = name;
            }

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
                if (t.isDaemon() != isDaemon) {
                    t.setDaemon(isDaemon);
                }
                if (t.getPriority() != Thread.NORM_PRIORITY) {
                    t.setPriority(Thread.NORM_PRIORITY);
                }
                return t;
            }
        }

        PooledFactory(String name) {
            this.name = name;
        }

        ThreadFactory getFactory(boolean isDaemon) {
            return new Factory(name + "-" + poolId.getAndIncrement() + "-thread-", isDaemon);
        }

    }

}
