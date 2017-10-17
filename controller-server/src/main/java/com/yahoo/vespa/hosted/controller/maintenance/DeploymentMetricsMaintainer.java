package com.yahoo.vespa.hosted.controller.maintenance;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Retrieve deployment metrics like qps and document count from the metric service and
 * update the applications with this info.
 *
 * @author smorgrav
 */
public class DeploymentMetricsMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(DeploymentMetricsMaintainer.class.getName());

    DeploymentMetricsMaintainer(Controller controller, Duration duration, JobControl jobControl) {
        super(controller, duration, jobControl);
    }

    @Override
    protected void maintain() {

        for (Application application : controller().applications().asList()) {
            for (Deployment deployment : application.deployments().values()) {
                try {
                    MetricsService.DeploymentMetrics metrics = controller().metricsService()
                            .getDeploymentMetrics(application.id(), deployment.zone());
                    DeploymentMetrics appMetrics = new DeploymentMetrics(metrics.queriesPerSecond(), metrics.writesPerSecond(),
                                                                         metrics.documentCount(), metrics.queryLatencyMillis(), metrics.writeLatencyMillis());

                    // Avoid locking for a long time, due to slow YAMAS.
                    try (Lock lock = controller().applications().lock(application.id())) {
                        // Deployment (or application) may have changed (or be gone) now:
                        controller().applications().get(application.id()).ifPresent(freshApplication -> {
                            Deployment freshDeployment = freshApplication.deployments().get(deployment.zone());
                            if (freshDeployment != null)
                                controller().applications().store(freshApplication.with(freshDeployment.withMetrics(appMetrics)), lock);
                        });
                    }
                }
                catch (UncheckedIOException e) {
                    log.warning("Timed out talking to YAMAS; retrying in " + maintenanceInterval() + ":\n" + e);
                }

            }

        }

    }

}

