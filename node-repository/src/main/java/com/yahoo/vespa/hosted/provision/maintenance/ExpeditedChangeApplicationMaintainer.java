// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This maintainer detects changes to nodes that must be expedited, and redeploys affected applications.
 *
 * The purpose of this is to redeploy affected applications faster than achieved by
 * {@link PeriodicApplicationMaintainer}, to reduce the time period where the node repository and the application model
 * is out of sync.
 * 
 * Why can't the manual change directly make the application redeployment?
 *
 * Because we want to queue redeployments to avoid overloading config servers.
 *
 * @author bratseth
 * @author mpolden
 */
public class ExpeditedChangeApplicationMaintainer extends ApplicationMaintainer {
    
    ExpeditedChangeApplicationMaintainer(Deployer deployer, Metric metric, NodeRepository nodeRepository, Duration interval) {
        super(deployer, metric, nodeRepository, interval);
    }

    @Override
    protected Set<ApplicationId> applicationsNeedingMaintenance() {
        Map<ApplicationId, NodeList> nodesByApplication = nodeRepository().nodes().list()
                                                                          .nodeType(NodeType.tenant, NodeType.proxy)
                                                                          .matching(node -> node.allocation().isPresent())
                                                                          .groupingBy(node -> node.allocation().get().owner());
        return nodesByApplication.entrySet().stream()
                                 .filter(entry -> hasNodesWithChanges(entry.getKey(), entry.getValue()))
                                 .map(Map.Entry::getKey)
                                 .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Deploy in the maintenance thread to avoid scheduling multiple deployments of the same application if it takes
     * longer to deploy than the (short) maintenance interval of this
     */
    @Override
    protected void deploy(ApplicationId application) {
        boolean deployed = deployWithLock(application);
        if (deployed)
            log.info("Redeployed application " + application.toShortString() +
                     " as an expedited change was made to its nodes");
    }

    private boolean hasNodesWithChanges(ApplicationId applicationId, NodeList nodes) {
        Optional<Instant> lastDeployTime = deployer().lastDeployTime(applicationId);
        if (lastDeployTime.isEmpty()) return false;

        return nodes.stream()
                    .flatMap(node -> node.history().events().stream())
                    .filter(event -> expediteChangeBy(event.agent()))
                    .map(History.Event::at)
                    .anyMatch(e -> lastDeployTime.get().isBefore(e));
    }

    @Override
    protected boolean canDeployNow(ApplicationId application) {
        return activeNodesByApplication().get(application) != null;
    }

    @Override
    protected Map<ApplicationId, NodeList> activeNodesByApplication() {
        return nodeRepository().nodes()
                               .list(Node.State.active)
                               .not().tester()
                               .groupingBy(node -> node.allocation().get().owner());
    }

    /** Returns whether to expedite changes performed by agent */
    private boolean expediteChangeBy(Agent agent) {
        switch (agent) {
            case operator:
            case RebuildingOsUpgrader:
            case HostEncrypter: return true;
        }
        return false;
    }

}
