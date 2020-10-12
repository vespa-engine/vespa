// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.MaintenanceDeployment.Move;
import com.yahoo.vespa.hosted.provision.node.Agent;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Ensure that nodes within a cluster a spread across hosts on exclusive network switches.
 *
 * @author mpolden
 */
public class SwitchRebalancer extends NodeMover<Move> {

    private final Metric metric;
    private final Deployer deployer;

    public SwitchRebalancer(NodeRepository nodeRepository, Duration interval, Metric metric, Deployer deployer) {
        super(deployer, nodeRepository, interval, metric, Move.empty());
        this.deployer = deployer;
        this.metric = metric;
    }

    @Override
    protected boolean maintain() {
        boolean success = true;
        // Using node list without holding lock as strong consistency is not needed here
        NodeList allNodes = nodeRepository().list();
        if (!zoneIsStable(allNodes)) return success;
        findBestMove(allNodes).execute(false, Agent.SwitchRebalancer, deployer, metric, nodeRepository());
        return success;
    }

    @Override
    protected Move suggestedMove(Node node, Node fromHost, Node toHost, NodeList allNodes) {
        NodeList clusterNodes = clusterOf(node, allNodes);
        NodeList clusterHosts = allNodes.parentsOf(clusterNodes);
        if (isBalanced(clusterNodes, clusterHosts)) return Move.empty();
        if (switchInUse(toHost, clusterHosts)) return Move.empty();
        return new Move(node, fromHost, toHost);
    }

    @Override
    protected Move bestMoveOf(Move a, Move b) {
        if (b.isEmpty()) return a;
        return b;
    }

    private NodeList clusterOf(Node node, NodeList allNodes) {
        ApplicationId application = node.allocation().get().owner();
        ClusterSpec.Id cluster = node.allocation().get().membership().cluster().id();
        return allNodes.state(Node.State.active)
                       .owner(application)
                       .cluster(cluster);
    }

    /** Returns whether switch of host is already in use by given cluster */
    private boolean switchInUse(Node host, NodeList clusterHosts) {
        if (host.switchHostname().isEmpty()) return false;
        for (var clusterHost : clusterHosts) {
            if (clusterHost.switchHostname().isEmpty()) continue;
            if (clusterHost.switchHostname().get().equals(host.switchHostname().get())) return true;
        }
        return false;
    }

    /** Returns whether given cluster nodes are balanced optimally on exclusive switches */
    private boolean isBalanced(NodeList clusterNodes, NodeList clusterHosts) {
        Set<String> switches = new HashSet<>();
        int exclusiveSwitches = 0;
        for (var host : clusterHosts) {
            if (host.switchHostname().isEmpty()) {
                exclusiveSwitches++; // Unknown switch counts as exclusive
            } else {
                switches.add(host.switchHostname().get());
            }
        }
        exclusiveSwitches += switches.size();
        return clusterNodes.size() <= exclusiveSwitches;
    }

}
