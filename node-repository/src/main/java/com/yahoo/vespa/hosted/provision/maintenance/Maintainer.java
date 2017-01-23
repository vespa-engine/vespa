// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.yolean.Exceptions;

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

    protected static final Logger log = Logger.getLogger(Maintainer.class.getName());

    private final NodeRepository nodeRepository;
    private final Duration rate;

    private final ScheduledExecutorService service;

    public Maintainer(NodeRepository nodeRepository, Duration rate) {
        this.nodeRepository = nodeRepository;
        this.rate = rate;

        this.service = new ScheduledThreadPoolExecutor(1);
        this.service.scheduleAtFixedRate(this, rate.toMillis(), rate.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Returns the node repository */
    protected NodeRepository nodeRepository() { return nodeRepository; }

    protected static Duration min(Duration a, Duration b) {
        return a.toMillis() < b.toMillis() ? a : b;
    }

    /** Returns the rate at which this job is set to run */
    protected Duration rate() { return rate; }

    @Override
    public void run() {
        try {
            maintain();
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, this + " failed. Will retry in " + rate.toMinutes() + " minutes", e);
        }
    }

    @Override
    public void deconstruct() {
        this.service.shutdown();
    }

    /** Returns a textual description of this job */
    @Override
    public abstract String toString();

    /** Called once each time this maintenance job should run */
    protected abstract void maintain();

}
