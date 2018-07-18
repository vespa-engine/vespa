// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.TenantName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.service.monitor.application.ConfigServerApplication;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The application maintainer regularly redeploys all applications to make sure the node repo and application
 * model is in sync and to trigger background node allocation changes such as allocation optimizations and
 * flavor retirement.
 *
 * @author bratseth
 */
public class PeriodicApplicationMaintainer extends ApplicationMaintainer {
    private final Duration minTimeBetweenRedeployments;

    public PeriodicApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, 
                                         Duration interval, Duration minTimeBetweenRedeployments, JobControl jobControl) {
        super(deployer, nodeRepository, interval, jobControl);
        this.minTimeBetweenRedeployments = minTimeBetweenRedeployments;
    }

    @Override
    protected boolean canDeployNow(ApplicationId application) {
        // Don't deploy if a regular deploy just happened
        return getLastDeployTime(application).isBefore(nodeRepository().clock().instant().minus(minTimeBetweenRedeployments));
    }

    // Returns the app that was deployed the longest time ago
    @Override
    protected Set<ApplicationId> applicationsNeedingMaintenance() {
        Optional<ApplicationId> app = (nodesNeedingMaintenance().stream()
                .map(node -> node.allocation().get().owner())
                .filter(applicationId -> !ConfigServerApplication.CONFIG_SERVER_APPLICATION.getApplicationId().equals(applicationId))
                .min(Comparator.comparing(this::getLastDeployTime)));
        app.ifPresent(applicationId -> log.log(LogLevel.INFO, applicationId + " will be deployed, last deploy time " +
                getLastDeployTime(applicationId)));
        return app.map(applicationId -> new HashSet<>(Collections.singletonList(applicationId))).orElseGet(HashSet::new);
    }

    private Instant getLastDeployTime(ApplicationId application) {
        return deployer().lastDeployTime(application).orElse(Instant.EPOCH);
    }

    @Override
    protected List<Node> nodesNeedingMaintenance() {
        return nodeRepository().getNodes(Node.State.active);
    }

}
