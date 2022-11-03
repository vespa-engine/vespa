// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    protected Map<ApplicationId, String> applicationsNeedingMaintenance() {
        var applications = new HashMap<ApplicationId, String>();

        nodeRepository().nodes()
                        .list()
                        .nodeType(NodeType.tenant, NodeType.proxy)
                        .matching(node -> node.allocation().isPresent())
                        .groupingBy(node -> node.allocation().get().owner())
                        .forEach((applicationId, nodes) -> {
                            hasNodesWithChanges(applicationId, nodes)
                                    .ifPresent(reason -> applications.put(applicationId, reason));
                        });

        // A ready proxy node should trigger a redeployment as it will activate the node.
        if (!nodeRepository().nodes().list(Node.State.ready, Node.State.reserved).nodeType(NodeType.proxy).isEmpty()) {
            applications.merge(ApplicationId.from("hosted-vespa", "routing", "default"),
                               "nodes being ready",
                               (oldValue, newValue) -> oldValue + ", " + newValue);
        }

        return applications;
    }

    /**
     * Deploy in the maintenance thread to avoid scheduling multiple deployments of the same application if it takes
     * longer to deploy than the (short) maintenance interval of this
     */
    @Override
    protected void deploy(ApplicationId application, String reason) {
        deployWithLock(application, reason);
    }

    /** Returns the reason for doing an expedited deploy. */
    private Optional<String> hasNodesWithChanges(ApplicationId applicationId, NodeList nodes) {
        Optional<Instant> lastDeployTime = deployer().lastDeployTime(applicationId);
        if (lastDeployTime.isEmpty()) return Optional.empty();

        List<String> reasons = nodes.stream()
                                    .flatMap(node -> node.history()
                                                            .events()
                                                            .stream()
                                                            .filter(event -> expediteChangeBy(event.agent()))
                                                            .filter(event -> lastDeployTime.get().isBefore(event.at()))
                                                            .map(event -> event.type() + (event.agent() == Agent.system ? "" : " by " + event.agent())))
                                    .sorted()
                                    .distinct()
                                    .collect(Collectors.toList());

        return reasons.isEmpty() ?
                Optional.empty() :
                Optional.of("recent node events: [" + String.join(", ", reasons) + "]");
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
        return switch (agent) {
            case operator, HostEncrypter, HostResumeProvisioner, RebuildingOsUpgrader -> true;
            default -> false;
        };
    }

}
