// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
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
    private final Instant start;

    public PeriodicApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, 
                                         Duration interval, Duration minTimeBetweenRedeployments, JobControl jobControl) {
        super(deployer, nodeRepository, interval, jobControl);
        this.minTimeBetweenRedeployments = minTimeBetweenRedeployments;
        this.start = Instant.now();
    }

    @Override
    protected boolean canDeployNow(ApplicationId application) {
        // Don't deploy if a regular deploy just happened
        return getLastDeployTime(application).isBefore(nodeRepository().clock().instant().minus(minTimeBetweenRedeployments));
    }

    // Returns the app that was deployed the longest time ago
    @Override
    protected Set<ApplicationId> applicationsNeedingMaintenance() {
        if (waitInitially()) return Collections.emptySet();

        Optional<ApplicationId> app = (nodesNeedingMaintenance().stream()
                .map(node -> node.allocation().get().owner())
                .distinct()
                .filter(this::shouldBeDeployedOnThisServer)
                .min(Comparator.comparing(this::getLastDeployTime)))
                .filter(this::canDeployNow);
        app.ifPresent(applicationId -> log.log(LogLevel.INFO, applicationId + " will be deployed, last deploy time " +
                getLastDeployTime(applicationId)));
        return app.map(Collections::singleton).orElseGet(Collections::emptySet);
    }

    private Instant getLastDeployTime(ApplicationId application) {
        return deployer().lastDeployTime(application).orElse(Instant.EPOCH);
    }

    // We only know last deploy time for applications that were deployed on this config server,
    // the rest will be deployed on another config server
    protected boolean shouldBeDeployedOnThisServer(ApplicationId application) {
        return deployer().lastDeployTime(application).isPresent();
    }

    // TODO: Do not start deploying until some time has gone (ideally only until bootstrap of config server is finished)
    protected boolean waitInitially() {
        return Instant.now().isBefore(start.plus(minTimeBetweenRedeployments));
    }

    @Override
    protected List<Node> nodesNeedingMaintenance() {
        return nodeRepository().getNodes(Node.State.active);
    }

}
