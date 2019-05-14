// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.RotationStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
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
        super(controller, duration, jobControl);
        this.applications = controller.applications();
    }

    @Override
    protected void maintain() {
        AtomicInteger failures = new AtomicInteger(0);
        AtomicReference<Exception> lastException = new AtomicReference<>(null);
        List<Application> applicationList = applications.asList();

        // Run parallel stream inside a custom ForkJoinPool so that we can control the number of threads used
        ForkJoinPool pool = new ForkJoinPool(applicationsToUpdateInParallel);
        pool.submit(() -> {
            applicationList.parallelStream().forEach(application -> {
                try {
                    applications.lockIfPresent(application.id(), locked ->
                            applications.store(locked.with(controller().metricsService().getApplicationMetrics(application.id()))));

                    applications.lockIfPresent(application.id(), locked ->
                            applications.store(locked.withRotationStatus(rotationStatus(application))));

                    for (Deployment deployment : application.deployments().values()) {
                        MetricsService.DeploymentMetrics collectedMetrics = controller().metricsService()
                                                                                        .getDeploymentMetrics(application.id(), deployment.zone());
                        Instant now = controller().clock().instant();
                        applications.lockIfPresent(application.id(), locked -> {
                            Deployment existingDeployment = locked.get().deployments().get(deployment.zone());
                            if (existingDeployment == null) return; // Deployment removed since we started collecting metrics
                            DeploymentMetrics newMetrics = existingDeployment.metrics()
                                                                             .withQueriesPerSecond(collectedMetrics.queriesPerSecond())
                                                                             .withWritesPerSecond(collectedMetrics.writesPerSecond())
                                                                             .withDocumentCount(collectedMetrics.documentCount())
                                                                             .withQueryLatencyMillis(collectedMetrics.queryLatencyMillis())
                                                                             .withWriteLatencyMillis(collectedMetrics.writeLatencyMillis())
                                                                             .at(now);
                            applications.store(locked.with(existingDeployment.zone(), newMetrics)
                                                     .recordActivityAt(now, existingDeployment.zone()));
                        });
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                    lastException.set(e);
                }
            });
        });
        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.MINUTES);
            if (lastException.get() != null) {
                log.log(Level.INFO, String.format("Failed to query metrics service for %d/%d applications. Retrying in %s. Last error: ",
                                                     failures.get(),
                                                     applicationList.size(),
                                                     maintenanceInterval()),
                        lastException.get());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get global rotation status for application */
    private Map<HostName, RotationStatus> rotationStatus(Application application) {
        return applications.rotationRepository().getRotation(application)
                           .map(rotation -> controller().metricsService().getRotationStatus(rotation.name()))
                           .map(rotationStatus -> {
                               Map<HostName, RotationStatus> result = new TreeMap<>();
                               rotationStatus.forEach((hostname, status) -> result.put(hostname, from(status)));
                               return result;
                           })
                           .orElseGet(Collections::emptyMap);
    }

    private static RotationStatus from(com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus status) {
        switch (status) {
            case IN: return RotationStatus.in;
            case OUT: return RotationStatus.out;
            case UNKNOWN: return RotationStatus.unknown;
            default: throw new IllegalArgumentException("Unknown API value for rotation status: " + status);
        }
    }

}
