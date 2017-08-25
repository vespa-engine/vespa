// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;

import java.io.UncheckedIOException;
import java.time.Duration;

/**
 * This maintenance job periodically updates the version status.
 * Since the version status is expensive to compute and do not need to be perfectly up to date,
 * we do not want to recompute it each time it is accessed.
 * 
 * @author bratseth
 */
public class VersionStatusUpdater extends Maintainer {

    public VersionStatusUpdater(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    protected void maintain() {
        try {
            VersionStatus newStatus = VersionStatus.compute(controller());
            controller().updateVersionStatus(newStatus);
        } catch (UncheckedIOException e) {
            log.warning("Failed to compute version status. This is likely a transient error: " + e.getMessage());
        }
    }

}
