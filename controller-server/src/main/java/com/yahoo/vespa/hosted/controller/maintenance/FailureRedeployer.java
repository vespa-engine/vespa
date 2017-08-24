// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Attempts redeployment of failed jobs and deployments.
 * 
 * @author bratseth
 */
public class FailureRedeployer extends Maintainer {
    
    public FailureRedeployer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    public void maintain() {
        ApplicationList applications = ApplicationList.from(controller().applications().asList()).isDeploying();
        List<Application> toTrigger = new ArrayList<>();

        // Applications with deployment failures for current change and no running jobs
        toTrigger.addAll(applications.hasDeploymentFailures()
                                 .notRunningJob()
                                 .asList());

        // Applications with jobs that have been in progress for more than 12 hours
        Instant twelveHoursAgo = controller().clock().instant().minus(Duration.ofHours(12));
        toTrigger.addAll(applications.jobRunningSince(twelveHoursAgo).asList());

        toTrigger.forEach(application -> controller().applications().deploymentTrigger()
                .triggerFailing(application.id()));
    }

}
