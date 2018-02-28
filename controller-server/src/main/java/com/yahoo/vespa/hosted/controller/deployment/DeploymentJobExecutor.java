// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.Maintainer;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Triggers deployment jobs in an external BuildService.
 *
 * Triggering is performed by an Executor, as there is no guarantee the BuildService provides a timely response.
 *
 * @author jvenstad
 */
public class DeploymentJobExecutor extends Maintainer {

    private static final Logger log = Logger.getLogger(DeploymentJobExecutor.class.getName());
    private static final int triggeringRetries = 5;

    private final BuildService buildService;
    private final Executor executor;

    public DeploymentJobExecutor(Controller controller, Duration triggeringInterval, JobControl jobControl, BuildService buildService) {
        this(controller, triggeringInterval, jobControl, buildService, Executors.newFixedThreadPool(20));
    }

    DeploymentJobExecutor(Controller controller, Duration triggeringInterval, JobControl jobControl,
                          BuildService buildService, Executor executor) {
        super(controller, triggeringInterval, jobControl);
        this.buildService = buildService;
        this.executor = executor;
    }

    @Override
    protected void maintain() {
        controller().applications().deploymentTrigger().deploymentQueue().takeJobsToRun()
                .forEach(buildJob -> executor.execute(() -> {
                    log.log(Level.INFO, "Attempting to trigger " + buildJob + " in Screwdriver.");
                    for (int i = 0; i < triggeringRetries; i++)
                        if (buildService.trigger(buildJob))
                            return;

                    log.log(Level.WARNING, "Exhausted all " + triggeringRetries + " retries for " + buildJob + " without success.");
                }));
    }

}
