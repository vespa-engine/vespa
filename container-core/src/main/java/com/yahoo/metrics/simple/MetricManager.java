// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadLocalDirectory;
import com.yahoo.concurrent.ThreadLocalDirectory.Updater;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.metrics.ManagerConfig;
import java.util.logging.Level;

/**
 * This is the coordinating class owning the executor and the top level objects
 * for measured metrics.
 *
 * @author Steinar Knutsen
 */
public class MetricManager extends AbstractComponent implements Provider<MetricReceiver> {

    private static final Logger log = Logger.getLogger(MetricManager.class.getName());

    private final ScheduledThreadPoolExecutor executor;
    private final MetricReceiver receiver;
    private final ThreadLocalDirectory<Bucket, Sample> metricsCollection;

    public MetricManager(ManagerConfig settings) {
        this(settings, new MetricUpdater());
    }

    private MetricManager(ManagerConfig settings, Updater<Bucket, Sample> updater) {
        log.log(Level.CONFIG, "setting up simple metrics gathering." +
                              " reportPeriodSeconds=" + settings.reportPeriodSeconds() +
                              ", pointsToKeepPerMetric=" + settings.pointsToKeepPerMetric());
        metricsCollection = new ThreadLocalDirectory<>(updater);
        final AtomicReference<Bucket> currentSnapshot = new AtomicReference<>(null);
        executor = new ScheduledThreadPoolExecutor(1);
        // Fixed rate, not fixed delay, is it is not too important that each
        // bucket has data for exactly one second, but one should strive for
        // this.buffer to contain data for as close a period to the report
        // interval as possible
        executor.scheduleAtFixedRate(new MetricAggregator(metricsCollection, currentSnapshot, settings),
                                     1,
                                     1, TimeUnit.SECONDS);
        receiver = new MetricReceiver(metricsCollection, currentSnapshot);
    }

    static MetricManager constructWithCustomUpdater(ManagerConfig settings, Updater<Bucket, Sample> updater) {
        return new MetricManager(settings, updater);
    }


    @Override
    public void deconstruct() {
        executor.shutdown();
    }

    @Override
    public MetricReceiver get() {
        return receiver;
    }

}
