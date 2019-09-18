// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.QrConfig;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All users will get the same pool instance.
 *
 * @author baldersheim
 */
public class FS4ResourcePool extends AbstractComponent {

    private static final Logger logger = Logger.getLogger(FS4ResourcePool.class.getName());
    private static final AtomicInteger instanceCounter = new AtomicInteger(0);
    private final String serverId;
    private final int instanceId;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduledExecutor;

    @Inject
    public FS4ResourcePool(QrConfig config) {
        this(config.discriminator());
    }
    
    public FS4ResourcePool(String serverId) {
        this.serverId = serverId;
        instanceId = instanceCounter.getAndIncrement();
        String name = "FS4-" + instanceId;
        executor = Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory(name));
        scheduledExecutor = Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory(name + ".scheduled"));
    }

    /** Returns an unique identifier of the server this runs in */
    public String getServerId() { return serverId; }
    public ExecutorService getExecutor() { return executor; }
    public ScheduledExecutorService getScheduledExecutor() { return scheduledExecutor; }

    @Override
    public void deconstruct() {
        logger.log(Level.INFO, "Deconstructing FS4ResourcePool with id '" + instanceId + "'.");
        super.deconstruct();
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
