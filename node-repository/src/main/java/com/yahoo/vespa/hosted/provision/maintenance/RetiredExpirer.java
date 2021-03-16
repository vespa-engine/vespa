// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maintenance job which deactivates retired nodes, if given permission by orchestrator, or
 * after the system has been given sufficient time to migrate data to other nodes.
 *
 * @author hakon
 */
public class RetiredExpirer extends NodeRepositoryMaintainer {

    private static final int NUM_CONFIG_SERVERS = 3;

    private final Deployer deployer;
    private final Metric metric;
    private final Orchestrator orchestrator;
    private final Duration retiredExpiry;

    public RetiredExpirer(NodeRepository nodeRepository,
                          Orchestrator orchestrator,
                          Deployer deployer,
                          Metric metric,
                          Duration maintenanceInterval,
                          Duration retiredExpiry) {
        super(nodeRepository, maintenanceInterval, metric);
        this.deployer = deployer;
        this.metric = metric;
        this.orchestrator = orchestrator;
        this.retiredExpiry = retiredExpiry;
    }

    @Override
    protected boolean maintain() {
        NodeList activeNodes = nodeRepository().nodes().list(Node.State.active);
        Map<ApplicationId, NodeList> retiredNodesByApplication = activeNodes.retired().groupingBy(node -> node.allocation().get().owner());
        for (Map.Entry<ApplicationId, NodeList> entry : retiredNodesByApplication.entrySet()) {
            ApplicationId application = entry.getKey();
            NodeList retiredNodes = entry.getValue();
            List<Node> nodesToRemove = retiredNodes.stream().filter(n -> canRemove(n, activeNodes)).collect(Collectors.toList());
            if (nodesToRemove.isEmpty()) continue;

            try (MaintenanceDeployment deployment = new MaintenanceDeployment(application, deployer, metric, nodeRepository())) {
                if ( ! deployment.isValid()) continue;

                nodeRepository().nodes().setRemovable(application, nodesToRemove);
                boolean success = deployment.activate().isPresent();
                if ( ! success) return success;
                String nodeList = nodesToRemove.stream().map(Node::hostname).collect(Collectors.joining(", "));
                log.info("Redeployed " + application + " to deactivate retired nodes: " +  nodeList);
            }
        }
        return true;
    }

    /**
     * Checks if the node can be removed:
     * if the node is a host, it will only be removed if it has no children,
     * or all its children are parked or failed.
     * Otherwise, a removal is allowed if either of these are true:
     * - The node has been in state {@link History.Event.Type#retired} for longer than {@link #retiredExpiry}
     * - Orchestrator allows it
     */
    private boolean canRemove(Node node, NodeList activeNodes) {
        if (node.type().isHost()) {
            if (nodeRepository().nodes().list().childrenOf(node).asList().stream()
                                .allMatch(child -> child.state() == Node.State.parked ||
                                                   child.state() == Node.State.failed)) {
                log.info("Host " + node + " has no non-parked/failed children");
                return true;
            }

            return false;
        }

        if (node.type().isConfigServerLike()) {
            // Avoid eventual expiry of configserver-like nodes

            if (activeNodes.nodeType(node.type()).size() < NUM_CONFIG_SERVERS) {
                // Scenario:  All 3 config servers want to retire.
                //
                // Say RetiredExpirer runs on cfg1 and gives cfg2 permission to be removed (PERMANENTLY_DOWN in ZK).
                // The consequent redeployment moves cfg2 to inactive, removing cfg2 from the application,
                // and PERMANENTLY_DOWN for cfg2 is cleaned up.
                //
                // If the RetiredExpirer on cfg3 now runs before its InfrastructureProvisioner, then
                //  a. The duper model still contains cfg2
                //  b. The service model still monitors cfg2 for health and it is UP
                //  c. The Orchestrator has no host status (like PERMANENTLY_DOWN) for cfg2,
                //     which is equivalent to NO_REMARKS
                // Therefore, from the point of view of the Orchestrator invoked below, any cfg will
                // be allowed to be removed, say cfg1.  In the subsequent redeployment, both cfg2
                // and cfg1 are now inactive.
                //
                // A proper solution would be to ensure the duper model is changed atomically
                // with node states across all config servers.  As this would require some work,
                // we will instead verify here that there are 3 active config servers before
                // allowing the removal of any config server.
                return false;
            }
        } else if (node.history().hasEventBefore(History.Event.Type.retired, clock().instant().minus(retiredExpiry))) {
            log.warning("Node " + node + " has been retired longer than " + retiredExpiry + ": Allowing removal. This may cause data loss");
            return true;
        }

        try {
            orchestrator.acquirePermissionToRemove(new HostName(node.hostname()));
            log.info("Node " + node + " has been granted permission to be removed");
            return true;
        } catch (UncheckedTimeoutException e) {
            log.warning("Timed out trying to acquire permission to remove " + node.hostname() + ": " + Exceptions.toMessageString(e));
            return false;
        } catch (OrchestrationException e) {
            log.info("Did not get permission to remove retired " + node + ": " + Exceptions.toMessageString(e));
            return false;
        }
    }

}
