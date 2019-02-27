// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The application maintainer regularly redeploys all applications to make sure the node repo and application
 * model is in sync and to trigger background node allocation changes such as allocation optimizations and
 * flavor retirement.
 *
 * @author bratseth
 */
public class PeriodicApplicationMaintainer extends ApplicationMaintainer {

    private final Duration minTimeBetweenRedeployments;
    private final Clock clock;
    private final Instant start;

    public PeriodicApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, 
                                         Duration interval, Duration minTimeBetweenRedeployments, JobControl jobControl) {
        super(deployer, nodeRepository, interval, jobControl);
        this.minTimeBetweenRedeployments = minTimeBetweenRedeployments;
        this.clock = nodeRepository.clock();
        this.start = clock.instant();
    }

    @Override
    protected boolean canDeployNow(ApplicationId application) {
        // Don't deploy if a regular deploy just happened
        return getLastDeployTime(application).isBefore(nodeRepository().clock().instant().minus(minTimeBetweenRedeployments));
    }

    // Returns the applications that need to be redeployed by this config server at this point in time.
    @Override
    protected Set<ApplicationId> applicationsNeedingMaintenance() {
        if (waitInitially()) return Collections.emptySet();

        // Collect all deployment times before sorting as deployments may happen while we build the set, breaking
        // the comparable contract. Stale times are fine as the time is rechecked in ApplicationMaintainer#deployWithLock
        Map<ApplicationId, Instant> deploymentTimes = nodesNeedingMaintenance().stream()
                                                                               .map(node -> node.allocation().get().owner())
                                                                               .distinct()
                                                                               .filter(this::shouldBeDeployedOnThisServer)
                                                                               .filter(this::canDeployNow)
                                                                               .collect(Collectors.toMap(Function.identity(), this::getLastDeployTime));

        return deploymentTimes.entrySet().stream()
                              .sorted(Map.Entry.comparingByValue())
                              .map(Map.Entry::getKey)
                              .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // We only know last deploy time for applications that were deployed on this config server,
    // the rest will be deployed on another config server
    protected boolean shouldBeDeployedOnThisServer(ApplicationId application) {
        return deployer().lastDeployTime(application).isPresent();
    }

    // TODO: Do not start deploying until some time has gone (ideally only until bootstrap of config server is finished)
    private boolean waitInitially() {
        return clock.instant().isBefore(start.plus(minTimeBetweenRedeployments));
    }

    protected List<Node> nodesNeedingMaintenance() {
        return nodeRepository().getNodes(Node.State.active);
    }

}
