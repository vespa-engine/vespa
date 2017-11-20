// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.yolean.Exceptions;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrieve metrics for query and feed quality of service from a metrics service and store in each application.
 *
 * @author jvenstad
 */
public class ApplicationMetricsMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(ApplicationMetricsMaintainer.class.getName());

    ApplicationMetricsMaintainer(Controller controller, Duration duration, JobControl jobControl) {
        super(controller, duration, jobControl);
    }

    @Override
    protected void maintain() {
        boolean hasWarned = false;
        for (Application application : ApplicationList.from(controller().applications().asList()).notPullRequest().asList()) {
            try {
                MetricsService.ApplicationMetrics metrics = controller().metricsService()
                        .getApplicationMetrics(application.id());

                try (Lock lock = controller().applications().lock(application.id())) {
                    controller().applications().get(application.id(), lock)
                            .ifPresent(lockedApplication -> controller().applications().store(
                                    lockedApplication));
                }
            }
            catch (UncheckedIOException e) {
                if ( ! hasWarned) // Produce only one warning per maintenance interval.
                    log.log(Level.WARNING, "Failed talking to metrics service: " + Exceptions.toMessageString(e) +
                                           ". Retrying in " + maintenanceInterval());
                hasWarned = true;
            }
        }

    }

}
