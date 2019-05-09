// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InfraDeployer;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * Periodically deploys infrastructure applications.
 * TODO: Merge this with {@link PeriodicApplicationMaintainer}
 *
 * @author freva
 */
public class InfrastructureProvisioner extends Maintainer {

    private static final Logger logger = Logger.getLogger(InfrastructureProvisioner.class.getName());

    private final InfraDeployer infraDeployer;

    InfrastructureProvisioner(NodeRepository nodeRepository, InfraDeployer infraDeployer, Duration interval) {
        super(nodeRepository, interval);
        this.infraDeployer = infraDeployer;
    }

    @Override
    protected void maintain() {
        for (ApplicationId application : infraDeployer.getSupportedInfraApplications()) {
            try {
                infraDeployer.getDeployment(application).orElseThrow().activate();
            } catch (RuntimeException e) {
                logger.log(LogLevel.INFO, "Failed to activate " + application, e);
                // loop around to activate the next application
            }
        }
    }
}
