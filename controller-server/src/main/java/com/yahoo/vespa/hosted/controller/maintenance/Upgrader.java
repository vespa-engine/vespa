// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maintenance job which schedules applications for Vespa version upgrade
 * 
 * @author bratseth
 */
public class Upgrader extends Maintainer {

    private static final Logger log = Logger.getLogger(Upgrader.class.getName());

    public Upgrader(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    /**
     * Schedule application upgrades. Note that this implementation must be idempotent.
     */
    @Override
    public void maintain() {
        VespaVersion target = controller().versionStatus().version(controller().systemVersion());
        if (target == null) return; // we don't have information about the current system version at this time
        
        switch (target.confidence()) {
            case broken:
                ApplicationList toCancel = applications().upgradingTo(target.versionNumber())
                                                         .without(UpgradePolicy.canary);
                if (toCancel.isEmpty()) break;
                log.info("Version " + target.versionNumber() + " is broken, cancelling upgrades of non-canaries");
                cancelUpgradesOf(toCancel);
                break;
            case low:
                upgrade(applications().with(UpgradePolicy.canary), target.versionNumber());
                break;
            case normal:
                upgrade(applications().with(UpgradePolicy.defaultPolicy), target.versionNumber());
                break;
            case high:
                upgrade(applications().with(UpgradePolicy.conservative), target.versionNumber());
                break;
            default:
                throw new IllegalArgumentException("Unknown version confidence " + target.confidence());
        }
    }
    
    /** Returns a list of all applications */
    private ApplicationList applications() { return ApplicationList.from(controller().applications().asList()); }
    
    private void upgrade(ApplicationList applications, Version version) {
        Change.VersionChange change = new Change.VersionChange(version);
        cancelUpgradesOf(applications.upgradingToLowerThan(version));
        applications = applications.notPullRequest(); // Pull requests are deployed as separate applications to test then deleted; No need to upgrade
        applications = applications.onLowerVersionThan(version);
        applications = applications.notDeployingApplication(); // wait with applications deploying an application change
        applications = applications.notFailingOn(version); // try to upgrade only if it hasn't failed on this version
        applications = applications.notRunningJobFor(change); // do not trigger multiple jobs simultaneously for same upgrade
        applications = applications.canUpgradeAt(controller().clock().instant()); // wait with applications that are currently blocking upgrades
        for (Application application : applications.byIncreasingDeployedVersion().asList()) {
            try {
                controller().applications().deploymentTrigger().triggerChange(application.id(), change);
            } catch (IllegalArgumentException e) {
                log.log(Level.INFO, "Could not trigger change: " + Exceptions.toMessageString(e));
            }
        }
    }

    private void cancelUpgradesOf(ApplicationList applications) {
        for (Application application : applications.asList()) {
            controller().applications().deploymentTrigger().cancelChange(application.id());
        }
    }

}
