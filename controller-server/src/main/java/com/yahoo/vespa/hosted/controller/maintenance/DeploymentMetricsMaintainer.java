// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.yolean.Exceptions;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.logging.Level;
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
        boolean hasWarned = false;
        for (Application application : ApplicationList.from(controller().applications().asList()).notPullRequest().asList()) {
            try {
                controller().applications().lockedIfPresent(application.id(), lockedApplication ->
                        controller().applications().store(lockedApplication.with(controller().metricsService().getApplicationMetrics(application.id()))));

                for (Deployment deployment : application.deployments().values()) {
                    MetricsService.DeploymentMetrics deploymentMetrics = controller().metricsService()
                            .getDeploymentMetrics(application.id(), deployment.zone());
                    DeploymentMetrics appMetrics = new DeploymentMetrics(deploymentMetrics.queriesPerSecond(),
                                                                         deploymentMetrics.writesPerSecond(),
                                                                         deploymentMetrics.documentCount(),
                                                                         deploymentMetrics.queryLatencyMillis(),
                                                                         deploymentMetrics.writeLatencyMillis());

                    controller().applications().lockedIfPresent(application.id(), lockedApplication ->
                            controller().applications().store(lockedApplication.with(deployment.zone(), appMetrics)));
                }
            }
            catch (UncheckedIOException e) {
                if (!hasWarned) // produce only one warning per maintenance interval
                    log.log(Level.WARNING, "Failed talking to YAMAS: " + Exceptions.toMessageString(e) +
                                           ". Retrying in " + maintenanceInterval());
                hasWarned = true;
            }
        }
    }

}
