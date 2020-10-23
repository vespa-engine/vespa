// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
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
public class DeploymentExpirer extends ControllerMaintainer {

    public DeploymentExpirer(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected boolean maintain() {
        boolean success = true;
        for (Application application : controller().applications().readable()) {
            for (Instance instance : application.instances().values())
                for (Deployment deployment : instance.deployments().values()) {
                    if (!isExpired(deployment)) continue;

                    try {
                        log.log(Level.INFO, "Expiring deployment of " + instance.id() + " in " + deployment.zone());
                        controller().applications().deactivate(instance.id(), deployment.zone());
                    } catch (Exception e) {
                        success = false;
                        log.log(Level.WARNING, "Could not expire " + deployment + " of " + instance +
                                               ": " + Exceptions.toMessageString(e) + ". Retrying in " +
                                               interval());
                    }
                }
        }
        return success;
    }

    /** Returns whether given deployment has expired according to its TTL */
    private boolean isExpired(Deployment deployment) {
        if (deployment.zone().environment().isProduction()) return false; // Never expire production deployments
        return controller().zoneRegistry().getDeploymentTimeToLive(deployment.zone())
                           .map(timeToLive -> deployment.at().plus(timeToLive).isBefore(controller().clock().instant()))
                           .orElse(false);
    }

}
