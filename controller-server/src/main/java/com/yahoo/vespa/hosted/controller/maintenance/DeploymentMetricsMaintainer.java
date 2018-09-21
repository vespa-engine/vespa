// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.RotationStatus;
import com.yahoo.yolean.Exceptions;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrieve deployment metrics such as QPS and document count from the metric service and
 * update applications with this info.
 *
 * @author smorgrav
 */
public class DeploymentMetricsMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(DeploymentMetricsMaintainer.class.getName());

    private static final int applicationsToUpdateInParallel = 10;

    private final ApplicationController applications;

    DeploymentMetricsMaintainer(Controller controller, Duration duration, JobControl jobControl) {
        super(controller, duration, jobControl);
        this.applications = controller.applications();
    }

    @Override
    protected void maintain() {
        AtomicBoolean hasWarned = new AtomicBoolean(false);
        List<Application> applicationList = ApplicationList.from(applications.asList()).notPullRequest().asList();

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
                        MetricsService.DeploymentMetrics deploymentMetrics = controller().metricsService()
                                                                                         .getDeploymentMetrics(application.id(), deployment.zone());
                        DeploymentMetrics newMetrics = new DeploymentMetrics(deploymentMetrics.queriesPerSecond(),
                                                                             deploymentMetrics.writesPerSecond(),
                                                                             deploymentMetrics.documentCount(),
                                                                             deploymentMetrics.queryLatencyMillis(),
                                                                             deploymentMetrics.writeLatencyMillis());

                        applications.lockIfPresent(application.id(), locked ->
                                applications.store(locked.with(deployment.zone(), newMetrics)
                                                         .recordActivityAt(controller().clock().instant(), deployment.zone())));
                    }
                } catch (UncheckedIOException e) {
                    if (!hasWarned.getAndSet(true)) {// produce only one warning per maintenance interval
                        log.log(Level.WARNING, "Failed to query metrics service: " + Exceptions.toMessageString(e) +
                                               ". Retrying in " + maintenanceInterval());
                    }
                }
            });
        });
        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get global rotation status for application */
    private Map<HostName, RotationStatus> rotationStatus(Application application) {
        return application.rotation()
                          .map(rotation -> controller().metricsService().getRotationStatus(rotation.id().asString()))
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
