// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * Deploys application changes which have been postponed due to an ongoing upgrade, or a block window.
 *
 * @author bratseth
 */
public class OutstandingChangeDeployer extends ControllerMaintainer {

    private static final Logger logger = Logger.getLogger(OutstandingChangeDeployer.class.getName());

    public OutstandingChangeDeployer(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        double ok = 0, total = 0;
        for (Application application : ApplicationList.from(controller().applications().readable())
                                                      .withProjectId()
                                                      .withJobs()
                                                      .asList())
            try {
                ++total;
                controller().applications().deploymentTrigger().triggerNewRevision(application.id());
                ++ok;
            }
            catch (RuntimeException e) {
                logger.info("Failed triggering new revision for " + application + ": " + Exceptions.toMessageString(e));
            }
        return total > 0 ? ok / total : 1;
    }

}
