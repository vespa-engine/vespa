// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The application maintainer regularly redeploys all applications to make sure the node repo and application
 * model is in sync and to trigger background node allocation changes such as allocation optimizations and
 * flavor retirement.
 *
 * @author bratseth
 */
public class PeriodicApplicationMaintainer extends ApplicationMaintainer {

    public PeriodicApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, 
                                         Duration interval, JobControl jobControl) {
        super(deployer, nodeRepository, interval, jobControl);
    }

    @Override
    protected boolean canDeployNow(ApplicationId application) {
        Optional<Instant> lastDeploy = deployer().lastDeployTime(application);
        if (lastDeploy.isPresent() &&
            lastDeploy.get().isAfter(nodeRepository().clock().instant().minus(interval())))
            return false; // Don't deploy if a regular deploy just happened
        return true;
    }

    @Override
    protected List<Node> nodesNeedingMaintenance() {
        return nodeRepository().getNodes(Node.State.active);
    }

}
