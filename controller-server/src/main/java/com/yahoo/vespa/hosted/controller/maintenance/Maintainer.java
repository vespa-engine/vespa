// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private final String name;
    private final Set<SystemName> permittedSystems;

    public Maintainer(Controller controller, Duration interval, JobControl jobControl) {
        this(controller, interval, jobControl, null, EnumSet.allOf(SystemName.class));
    }

    public Maintainer(Controller controller, Duration interval, JobControl jobControl, String name, Set<SystemName> permittedSystems) {
        if (interval.isNegative() || interval.isZero())
            throw new IllegalArgumentException("Interval must be positive, but was " + interval);

        this.controller = controller;
        this.maintenanceInterval = interval;
        this.jobControl = jobControl;
        this.name = name;
        this.permittedSystems = ImmutableSet.copyOf(permittedSystems);

        service = new ScheduledThreadPoolExecutor(1);
        service.scheduleAtFixedRate(this, staggeredDelay(controller, interval), interval.toMillis(), TimeUnit.MILLISECONDS);
        jobControl.started(name());
    }
    
    protected Controller controller() { return controller; }
    
    @Override
    public void run() {
        try {
            if (!permittedSystems.contains(controller.system())) {
                return;
            }
            if (jobControl.isActive(name())) {
                try (Lock lock = jobControl.curator().lockMaintenanceJob(name())) {
                    maintain();
                }
            }
        }
        catch (TimeoutException e) {
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
    
    public final String name() {
        return name == null ? this.getClass().getSimpleName() : name;
    }
    
    /** Returns the name of this */
    @Override
    public final String toString() {
        return name();
    }

    private static long staggeredDelay(Controller controller, Duration interval) {
        List<HostName> cluster = controller.curator().cluster();
        if ( ! cluster.contains(controller.hostname()))
            return interval.toMillis();

        long timeUntilNextRun = Math.floorMod(-controller.clock().millis(), interval.toMillis() / cluster.size());
        return timeUntilNextRun + (1 + cluster.indexOf(controller.hostname())) * interval.toMillis() / cluster.size();
    }

}
