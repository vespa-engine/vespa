// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A maintainer is some job which runs at a fixed rate to perform some maintenance task on the node repo.
 *
 * @author bratseth
 */
public abstract class Maintainer extends AbstractComponent implements Runnable {

    protected final Logger log = Logger.getLogger(this.getClass().getName());

    private final NodeRepository nodeRepository;
    private final Duration interval;
    private final JobControl jobControl;

    private final ScheduledExecutorService service;

    public Maintainer(NodeRepository nodeRepository, Duration interval, JobControl jobControl) {
        this.nodeRepository = nodeRepository;
        this.interval = interval;
        this.jobControl = jobControl;

        service = new ScheduledThreadPoolExecutor(1);
        service.scheduleAtFixedRate(this, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        jobControl.started(name());
    }

    /** Returns the node repository */
    protected NodeRepository nodeRepository() { return nodeRepository; }

    protected static Duration min(Duration a, Duration b) {
        return a.toMillis() < b.toMillis() ? a : b;
    }

    /** Returns the interval at which this job is set to run */
    protected Duration interval() { return interval; }

    @Override
    public void run() {
        try {
            if (jobControl.isActive(name()))
                maintain();
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, this + " failed. Will retry in " + interval.toMinutes() + " minutes", e);
        }
    }

    @Override
    public void deconstruct() {
        this.service.shutdown();
    }

    /** Returns the simple name of this job */
    @Override
    public final String toString() { return name(); }

    /** Called once each time this maintenance job should run */
    protected abstract void maintain();
    
    private String name() { return this.getClass().getSimpleName(); }

}
