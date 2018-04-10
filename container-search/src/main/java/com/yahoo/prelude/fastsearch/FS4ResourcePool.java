// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.Server;
import com.yahoo.container.search.Fs4Config;
import com.yahoo.fs4.mplex.Backend;
import com.yahoo.fs4.mplex.ConnectionPool;
import com.yahoo.fs4.mplex.ListenerPool;
import com.yahoo.io.Connection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provider for {@link com.yahoo.fs4.mplex.ListenerPool}. All users will get the same pool instance.
 *
 * @author baldersheim
 */
public class FS4ResourcePool extends AbstractComponent {

    private static final Logger logger = Logger.getLogger(FS4ResourcePool.class.getName());
    private static final AtomicInteger instanceCounter = new AtomicInteger(0);
    private final int instanceId;
    private final ListenerPool listeners;
    private final Timer timer = new Timer();  // This is a timer for cleaning the closed connections
    private final Map<String, Backend> connectionPoolMap = new HashMap<>();
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduledExecutor;

    @Inject
    public FS4ResourcePool(Fs4Config fs4Config) {
        this(fs4Config.numlistenerthreads());
    }
    
    public FS4ResourcePool(int listenerThreads) {
        instanceId = instanceCounter.getAndIncrement();
        String name = "FS4-" + instanceId;
        listeners = new ListenerPool(name, listenerThreads);
        executor = Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory(name));
        scheduledExecutor = Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory(name + ".scheduled"));
    }

    public ExecutorService getExecutor() {
        return executor;
    }
    public ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    public Backend getBackend(String host, int port) {
        String key = host + ":" + port;
        synchronized (connectionPoolMap) {
            Backend pool = connectionPoolMap.get(key);
            if (pool == null) {
                pool = new Backend(host, port, Server.get().getServerDiscriminator(), listeners, new ConnectionPool(timer));
                connectionPoolMap.put(key, pool);
            }
            return pool;
        }
    }

    @Override
    public void deconstruct() {
        logger.log(Level.INFO, "Deconstructing FS4ResourcePool with id '" + instanceId + "'.");
        super.deconstruct();
        listeners.close();
        timer.cancel();
        for (Backend backend : connectionPoolMap.values()) {
            backend.shutdown();
            backend.close();
        }
        executor.shutdown();
        scheduledExecutor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
            scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warning("Executors failed terminating within timeout of 10 seconds : " + e);
        }
    }

}
