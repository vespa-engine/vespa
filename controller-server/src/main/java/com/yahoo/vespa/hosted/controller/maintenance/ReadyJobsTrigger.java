// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;

import java.time.Duration;

/**
 * Trigger ready deployment jobs. This drives jobs through each application's deployment pipeline.
 * 
 * @author bratseth
 */
public class ReadyJobsTrigger extends Maintainer {
    
    public ReadyJobsTrigger(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    public void maintain() {
        controller().applications().deploymentTrigger().triggerReadyJobs();
    }

}
