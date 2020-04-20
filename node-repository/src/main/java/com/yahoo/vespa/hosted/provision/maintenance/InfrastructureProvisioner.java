// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

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

    public void maintainButThrowOnException() {
        try {
            infraDeployer.activateAllSupportedInfraApplications(true);
        } catch (RuntimeException e) {
            logger.log(LogLevel.INFO, "Failed to deploy supported infrastructure applications, " +
                    "will sleep 30s before propagating failure, to allow inspection of zk",
                    e.getMessage());
            try { Thread.sleep(30_000); } catch (InterruptedException ignored) { }
            throw e;
        }
    }

    @Override
    protected void maintain() {
        infraDeployer.activateAllSupportedInfraApplications(false);
    }
}
