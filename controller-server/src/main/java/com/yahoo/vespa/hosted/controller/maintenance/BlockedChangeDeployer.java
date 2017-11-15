// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;

import java.time.Duration;

/**
 * Deploys application changes which have not made it to production because of a revision change block.
 * 
 * @author bratseth
 */
@SuppressWarnings("unused")
public class BlockedChangeDeployer extends Maintainer {
    
    public BlockedChangeDeployer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    protected void maintain() {
        controller().applications().deploymentTrigger().triggerReadyJobs();
    }

}
