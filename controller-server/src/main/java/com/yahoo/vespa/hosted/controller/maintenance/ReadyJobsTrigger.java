// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.TriggerResult;

import java.time.Duration;

/**
 * Trigger ready deployment jobs. This drives jobs through each application's deployment pipeline.
 * 
 * @author bratseth
 */
public class ReadyJobsTrigger extends ControllerMaintainer {
    
    public ReadyJobsTrigger(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    public double maintain() {
        TriggerResult result = controller().applications().deploymentTrigger().triggerReadyJobs();
        long total = result.triggered() + result.failed();
        return total == 0 ? 1 : (double) result.triggered() / (result.triggered() + result.failed());
    }

}
