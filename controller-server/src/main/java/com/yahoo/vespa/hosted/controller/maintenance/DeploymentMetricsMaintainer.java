package com.yahoo.vespa.hosted.controller.maintenance;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;

import java.time.Duration;

/**
 * Retrieve deployment metrics like qps and document count from the metric service and
 * update the applications with this info.
 *
 * @author smorgrav
 */
public class DeploymentMetricsMaintainer extends Maintainer {

    DeploymentMetricsMaintainer(Controller controller, Duration duration, JobControl jobControl) {
        super(controller, duration, jobControl);
    }

    @Override
    protected void maintain() {
        for (Application application : controller().applications().asList()) {
            try (Lock lock = controller().applications().lock(application.id())) {
                for (Deployment deployment : application.deployments().values()) {

                    MetricsService.DeploymentMetrics returnedMetrics = 
                            controller().metricsService().getDeploymentMetrics(application.id(), deployment.zone());

                    DeploymentMetrics metrics = new DeploymentMetrics(returnedMetrics.queriesPerSecond(),
                                                                      returnedMetrics.writesPerSecond(),
                                                                      returnedMetrics.documentCount(),
                                                                      returnedMetrics.queryLatencyMillis(),
                                                                      returnedMetrics.writeLatencyMillis());

                    controller().applications().store(application.with(deployment.withMetrics(metrics)), lock);
                }
            }
        }
    }

}
