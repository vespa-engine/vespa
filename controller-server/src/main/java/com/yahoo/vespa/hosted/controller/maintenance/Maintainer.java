// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A maintainer is some job which runs at a fixed interval to perform some maintenance task in the controller.
 *
 * @author bratseth
 */
public abstract class Maintainer extends AbstractComponent implements Runnable {

    protected static final Logger log = Logger.getLogger(Maintainer.class.getName());

    private final Controller controller;
    private final Duration maintenanceInterval;
    private final JobControl jobControl;
    private final ScheduledExecutorService service;

    public Maintainer(Controller controller, Duration interval, JobControl jobControl) {
        this.controller = controller;
        this.maintenanceInterval = interval;
        this.jobControl = jobControl;

        service = new ScheduledThreadPoolExecutor(1);
        service.scheduleAtFixedRate(this, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        jobControl.started(name());
    }
    
    protected Controller controller() { return controller; }
    
    @Override
    public void run() {
        try {
            if (jobControl.isActive(name())) {
                try (Lock lock = jobControl.curator().lockMaintenanceJob(name())) {
                    maintain();
                }
            }
        }
        catch (UncheckedTimeoutException e) {
            // another controller instance is running this job at the moment; ok
        }
        catch (Throwable t) {
            log.log(Level.WARNING, this + " failed. Will retry in " + maintenanceInterval.toMinutes() + " minutes", t);
        }
    }

    @Override
    public void deconstruct() {
        this.service.shutdown();
    }

    /** Called once each time this maintenance job should run */
    protected abstract void maintain();

    public Duration maintenanceInterval() { return maintenanceInterval; }
    
    public String name() { return this.getClass().getSimpleName(); }
    
    /** Returns the name of this */
    @Override
    public final String toString() {
        return name();
    }

}
