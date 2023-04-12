// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.logging.Level;

/**
 * @author mpolden
 */
public class OsVersionStatusUpdater extends ControllerMaintainer {

    public OsVersionStatusUpdater(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        try {
            OsVersionStatus newStatus = OsVersionStatus.compute(controller());
            controller().updateOsVersionStatus(newStatus);
            return 0.0;
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to compute OS version status: " + Exceptions.toMessageString(e) +
                                   ". Retrying in " + interval());
        }
        return 1.0;
    }

}
