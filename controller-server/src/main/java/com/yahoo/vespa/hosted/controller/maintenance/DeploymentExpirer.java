// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.logging.Level;

/**
 * Expires instances in zones that have configured expiration using TimeToLive.
 * 
 * @author mortent
 * @author bratseth
 */
public class DeploymentExpirer extends Maintainer {

    public DeploymentExpirer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    protected void maintain() {
        for (Application application : controller().applications().asList()) {
            for (Deployment deployment : application.deployments().values()) {
                if (!isExpired(deployment)) continue;

                try {
                    controller().applications().deactivate(application.id(), deployment.zone());
                } catch (Exception e) {
                    log.log(Level.WARNING, "Could not expire " + deployment + " of " + application +
                                           ": " + Exceptions.toMessageString(e) + ". Retrying in " +
                                           maintenanceInterval());
                }
            }
        }
    }

    /** Returns whether given deployment has expired according to its TTL */
    private boolean isExpired(Deployment deployment) {
        if (deployment.zone().environment().isProduction()) return false; // Never expire production deployments
        return controller().zoneRegistry().getDeploymentTimeToLive(deployment.zone())
                           .map(timeToLive -> deployment.at().plus(timeToLive).isBefore(controller().clock().instant()))
                           .orElse(false);
    }

}
