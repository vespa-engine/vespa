// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.NodesAndHosts;
import com.yahoo.vespa.hosted.provision.maintenance.MaintenanceDeployment.Move;
import com.yahoo.vespa.hosted.provision.node.Agent;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Ensure that nodes within a cluster a spread across hosts on exclusive network switches.
 *
 * @author mpolden
 */
public class SwitchRebalancer extends NodeMover<Move> {

    private static final Logger LOG = Logger.getLogger(SwitchRebalancer.class.getName());

    private final Metric metric;
    private final Deployer deployer;

    public SwitchRebalancer(NodeRepository nodeRepository, Duration interval, Metric metric, Deployer deployer) {
        super(deployer, nodeRepository, interval, metric, Move.empty());
        this.deployer = deployer;
        this.metric = metric;
    }

    @Override
    protected double maintain() {
        if (!nodeRepository().nodes().isWorking()) return 0.0;
        if (!nodeRepository().zone().environment().isProduction()) return 1.0;
        NodesAndHosts<NodeList> allNodes = NodesAndHosts.create(nodeRepository().nodes().list()); // Lockless as strong consistency is not needed
        if (!zoneIsStable(allNodes.nodes())) return 1.0;

        Move bestMove = findBestMove(allNodes);
        if (!bestMove.isEmpty()) {
            LOG.info("Trying " + bestMove + " (" + bestMove.fromHost().switchHostname().orElse("<none>") +
                     " -> " + bestMove.toHost().switchHostname().orElse("<none>") + ")");
        }
        bestMove.execute(false, Agent.SwitchRebalancer, deployer, metric, nodeRepository());
        return 1.0;
    }

    @Override
    protected Move suggestedMove(Node node, Node fromHost, Node toHost, NodesAndHosts<? extends NodeList> allNodes) {
        NodeList clusterNodes = clusterOf(node, allNodes.nodes());
        NodeList clusterHosts = allNodes.nodes().parentsOf(clusterNodes);
        if (onExclusiveSwitch(node, clusterHosts)) return Move.empty();
        if (!increasesExclusiveSwitches(clusterNodes, clusterHosts, toHost)) return Move.empty();
        return new Move(node, fromHost, toHost);
    }

    @Override
    protected Move bestMoveOf(Move a, Move b) {
        if (!a.isEmpty()) return a;
        return b;
    }

    private NodeList clusterOf(Node node, NodeList allNodes) {
        ApplicationId application = node.allocation().get().owner();
        ClusterSpec.Id cluster = node.allocation().get().membership().cluster().id();
        return allNodes.state(Node.State.active)
                       .owner(application)
                       .cluster(cluster);
    }

    /** Returns whether allocatedNode is on an exclusive switch */
    private static boolean onExclusiveSwitch(Node allocatedNode, NodeList clusterHosts) {
        return !NodeList.copyOf(List.of(allocatedNode)).onExclusiveSwitch(clusterHosts).isEmpty();
    }

    /** Returns whether allocating a node on toHost would increase the number of exclusive switches */
    private static boolean increasesExclusiveSwitches(NodeList clusterNodes, NodeList clusterHosts, Node toHost) {
        if (toHost.switchHostname().isEmpty()) return false;
        Set<String> activeSwitches = new HashSet<>();
        int unknownSwitches = 0;
        for (var host : clusterHosts) {
            if (host.switchHostname().isEmpty()) {
                unknownSwitches++;
            } else {
                activeSwitches.add(host.switchHostname().get());
            }
        }
        int exclusiveSwitches = unknownSwitches + activeSwitches.size();
        return clusterNodes.size() > exclusiveSwitches &&
               !activeSwitches.contains(toHost.switchHostname().get());
    }

}
