// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;

import java.time.Duration;

/**
 * Deploys application changes which have been postponed due to an ongoing upgrade, or a block window.
 *
 * @author bratseth
 */
public class OutstandingChangeDeployer extends ControllerMaintainer {

    public OutstandingChangeDeployer(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        for (Application application : ApplicationList.from(controller().applications().readable())
                                                      .withProductionDeployment()
                                                      .withDeploymentSpec()
                                                      .asList())
            controller().applications().deploymentTrigger().triggerNewRevision(application.id());
        return 1.0;
    }

}
