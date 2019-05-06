// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The application maintainer detects manual operator changes to nodes and redeploys affected applications.
 * The purpose of this is to redeploy affected applications faster than achieved by the regular application
 * maintenance to reduce the time period where the node repository and the application model is out of sync.
 * 
 * Why can't the manual change directly make the application redeployment?
 * Because the redeployment must run at the right config server, while the node state change may be running
 * at any config server.
 *
 * @author bratseth
 */
public class OperatorChangeApplicationMaintainer extends ApplicationMaintainer {

    private static final ApplicationId ZONE_APPLICATION_ID = ApplicationId.from("hosted-vespa", "routing", "default");

    private final Clock clock;
    
    private Instant previousRun;
    
    OperatorChangeApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, Clock clock, Duration interval) {
        super(deployer, nodeRepository, interval);
        this.clock = clock;
        previousRun = clock.instant(); // Changes before this will be caught by the first PeriodicApplicationMaintainer run
    }

    @Override
    protected Set<ApplicationId> applicationsNeedingMaintenance() {
        Instant windowEnd = clock.instant();
        Instant windowStart = previousRun;
        previousRun = windowEnd;
        return nodeRepository().getNodes().stream()
                               .filter(node -> hasManualStateChangeSince(windowStart, node))
                               .flatMap(node -> owner(node).stream())
                               .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean hasManualStateChangeSince(Instant instant, Node node) {
        return node.history().events().stream()
                .anyMatch(event -> event.agent() == Agent.operator && event.at().isAfter(instant));
    }

    private Optional<ApplicationId> owner(Node node) {
        if (node.allocation().isPresent()) return node.allocation().map(Allocation::owner);

        return node.type() == NodeType.host ? Optional.of(ZONE_APPLICATION_ID) : Optional.empty();
    }

    /**
     * Deploy in the maintenance thread to avoid scheduling multiple deployments of the same application if it takes
     * longer to deploy than the (short) maintenance interval of this
     */
    @Override
    protected void deploy(ApplicationId application) {
        deployWithLock(application);
        log.info("Redeployed application " + application.toShortString() +
                 " as a manual change was made to its nodes");
    }

}
