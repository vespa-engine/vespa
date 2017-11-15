// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;

import java.time.Duration;
import java.util.List;

/**
 * Attempts redeployment of failed jobs and deployments.
 * 
 * @author bratseth
 * @author mpolden
 */
public class FailureRedeployer extends Maintainer {

    public FailureRedeployer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    public void maintain() {
        List<Application> applications = ApplicationList.from(controller().applications().asList())
                .notPullRequest()
                .asList();
        applications.forEach(application -> triggerFailing(application));
    }

    private void triggerFailing(Application application) {
        controller().applications().deploymentTrigger().triggerFailing(application.id());
    }

}
