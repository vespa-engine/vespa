// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import java.util.logging.Level;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;

/**
 * @author mpolden
 */
public class OsVersionStatusUpdater extends Maintainer {

    public OsVersionStatusUpdater(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    protected void maintain() {
        try {
            OsVersionStatus newStatus = OsVersionStatus.compute(controller());
            controller().updateOsVersionStatus(newStatus);
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Failed to compute version status: " + Exceptions.toMessageString(e) +
                                      ". Retrying in " + maintenanceInterval());
        }
    }

}
