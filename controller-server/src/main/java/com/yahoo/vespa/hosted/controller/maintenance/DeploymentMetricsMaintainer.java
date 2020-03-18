// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Retrieve deployment metrics such as QPS and document count from the metric service and
 * update applications with this info.
 *
 * @author smorgrav
 * @author mpolden
 */
public class DeploymentMetricsMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(DeploymentMetricsMaintainer.class.getName());

    private static final int applicationsToUpdateInParallel = 10;

    private final ApplicationController applications;

    public DeploymentMetricsMaintainer(Controller controller, Duration duration, JobControl jobControl) {
        super(controller, duration, jobControl, DeploymentMetricsMaintainer.class.getSimpleName(), SystemName.all());
        this.applications = controller.applications();
    }

    @Override
    protected void maintain() {
        AtomicInteger failures = new AtomicInteger(0);
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicReference<Exception> lastException = new AtomicReference<>(null);

        // Run parallel stream inside a custom ForkJoinPool so that we can control the number of threads used
        ForkJoinPool pool = new ForkJoinPool(applicationsToUpdateInParallel);
        pool.submit(() ->
            applications.readable().parallelStream().forEach(application -> {
                for (Instance instance : application.instances().values())
                    for (Deployment deployment : instance.deployments().values()) {
                        attempts.incrementAndGet();
                        try {
                            if (deployment.version().getMajor() < 7) continue;
                            var collectedMetrics = controller().metrics().getDeploymentMetrics(instance.id(), deployment.zone());
                            var now = controller().clock().instant();
                            applications.lockApplicationIfPresent(application.id(), locked -> {
                                Deployment existingDeployment = locked.get().require(instance.name()).deployments().get(deployment.zone());
                                if (existingDeployment == null) return; // Deployment removed since we started collecting metrics
                                DeploymentMetrics newMetrics = existingDeployment.metrics()
                                                                                 .withQueriesPerSecond(collectedMetrics.queriesPerSecond())
                                                                                 .withWritesPerSecond(collectedMetrics.writesPerSecond())
                                                                                 .withDocumentCount(collectedMetrics.documentCount())
                                                                                 .withQueryLatencyMillis(collectedMetrics.queryLatencyMillis())
                                                                                 .withWriteLatencyMillis(collectedMetrics.writeLatencyMillis())
                                                                                 .at(now);
                                applications.store(locked.with(instance.name(),
                                                               lockedInstance -> lockedInstance.with(existingDeployment.zone(), newMetrics)
                                                                                               .recordActivityAt(now, existingDeployment.zone())));

                            });
                        } catch (Exception e) {
                            failures.incrementAndGet();
                            lastException.set(e);
                        }
                    }
            })
        );
        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.MINUTES);
            if (lastException.get() != null) {
                log.log(LogLevel.WARNING,
                        String.format("Failed to gather metrics for %d/%d applications. Retrying in %s. Last error: %s",
                                      failures.get(),
                                      attempts.get(),
                                      maintenanceInterval(),
                                      Exceptions.toMessageString(lastException.get())));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
