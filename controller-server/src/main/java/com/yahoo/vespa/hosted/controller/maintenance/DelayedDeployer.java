// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;

import java.time.Duration;

/**
 * Maintenance job which triggers jobs that have been delayed according to the applications deployment spec.
 *
 * @author mpolden
 */
public class DelayedDeployer extends Maintainer {

    public DelayedDeployer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    protected void maintain() {
        controller().applications().deploymentTrigger().triggerDelayed();
    }

}
