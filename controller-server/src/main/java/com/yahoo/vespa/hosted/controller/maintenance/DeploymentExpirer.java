// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

/**
 * Expires instances in zones that have configured expiration using TimeToLive.
 * 
 * @author mortent
 * @author bratseth
 */
public class DeploymentExpirer extends Maintainer {

    private final Clock clock;

    public DeploymentExpirer(Controller controller, Duration interval, JobControl jobControl) {
        this(controller, interval, Clock.systemUTC(), jobControl);
    }

    public DeploymentExpirer(Controller controller, Duration interval, Clock clock, JobControl jobControl) {
        super(controller, interval, jobControl);
        this.clock = clock;
    }

    @Override
    protected void maintain() {
        for (Application application : controller().applications().asList()) {
            for (Deployment deployment : application.deployments().values()) {
                if (deployment.zone().environment().equals(Environment.prod)) continue;

                if (hasExpired(controller().zoneRegistry(), deployment, clock.instant()))
                    try {
                        controller().applications().deactivate(application, deployment.zone());
                    }
                    catch (Exception e) {
                        log.log(Level.WARNING, "Could not expire " + deployment + " of " + application, e);
                    }
            }
        }
    }

    public static boolean hasExpired(ZoneRegistry zoneRegistry, Deployment deployment, Instant now) {
        return zoneRegistry.getDeploymentTimeToLive(deployment.zone())
                .map(timeToLive -> deployment.at().plus(timeToLive).isBefore(now))
                .orElse(false);
    }

}
