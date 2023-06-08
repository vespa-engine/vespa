// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import ai.vespa.metrics.ConfigServerMetrics;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.HostCapacity;

import java.time.Duration;

/**
 * @author bratseth
 */
public class Rebalancer extends NodeMover<Rebalancer.Move> {

    static final Duration waitTimeAfterPreviousDeployment = Duration.ofMinutes(10);

    private final Deployer deployer;
    private final Metric metric;

    public Rebalancer(Deployer deployer,
                      NodeRepository nodeRepository,
                      Metric metric,
                      Duration interval) {
        super(deployer, nodeRepository, interval, metric, Move.empty());
        this.deployer = deployer;
        this.metric = metric;
    }

    @Override
    protected double maintain() {
        if ( ! nodeRepository().nodes().isWorking()) return 0.0;

        if ( ! nodeRepository().zone().cloud().allowHostSharing()) return 1.0; // Rebalancing not necessary
        if (nodeRepository().zone().environment().isTest()) return 1.0; // Short lived deployments; no need to rebalance
        if (nodeRepository().zone().system().isCd()) return 1.0; // CD tests assert on # of nodes, avoid rebalnacing as it make tests unstable

        // Work with an unlocked snapshot as this can take a long time and full consistency is not needed
        NodeList allNodes = nodeRepository().nodes().list();
        updateSkewMetric(allNodes);
        if ( ! zoneIsStable(allNodes)) return 1.0;
        findBestMove(allNodes).execute(true, Agent.Rebalancer, deployer, metric, nodeRepository());
        return 1.0;
   }

    @Override
    protected Move suggestedMove(Node node, Node fromHost, Node toHost, NodeList allNodes) {
        HostCapacity capacity = new HostCapacity(allNodes, nodeRepository().resourcesCalculator());
        double skewReductionAtFromHost = skewReductionByRemoving(node, fromHost, capacity);
        double skewReductionAtToHost = skewReductionByAdding(node, toHost, capacity);
        double netSkewReduction = skewReductionAtFromHost + skewReductionAtToHost;
        return new Move(node, fromHost, toHost, netSkewReduction);
    }

    @Override
    protected Move bestMoveOf(Move a, Move b) {
        if (a.netSkewReduction >= b.netSkewReduction) return a;
        return b;
    }

    /** We do this here rather than in MetricsReporter because it is expensive and frequent updates are unnecessary */
    private void updateSkewMetric(NodeList allNodes) {
        HostCapacity capacity = new HostCapacity(allNodes, nodeRepository().resourcesCalculator());
        double totalSkew = 0;
        int hostCount = 0;
        for (Node host : allNodes.nodeType(NodeType.host).state(Node.State.active)) {
            hostCount++;
            totalSkew += Node.skew(host.flavor().resources(), capacity.unusedCapacityOf(host));
        }
        metric.set(ConfigServerMetrics.HOSTED_VESPA_DOCKER_SKEW.baseName(),totalSkew/hostCount, null);
    }

    private double skewReductionByRemoving(Node node, Node fromHost, HostCapacity capacity) {
        NodeResources freeHostCapacity = capacity.unusedCapacityOf(fromHost).justNumbers();
        double skewBefore = Node.skew(fromHost.flavor().resources(), freeHostCapacity);
        double skewAfter = Node.skew(fromHost.flavor().resources(), freeHostCapacity.add(node.flavor().resources().justNumbers()));
        return skewBefore - skewAfter;
    }

    private double skewReductionByAdding(Node node, Node toHost, HostCapacity capacity) {
        NodeResources freeHostCapacity = capacity.unusedCapacityOf(toHost).justNumbers();
        double skewBefore = Node.skew(toHost.flavor().resources(), freeHostCapacity);
        double skewAfter = Node.skew(toHost.flavor().resources(), freeHostCapacity.subtract(node.resources().justNumbers()));
        return skewBefore - skewAfter;
    }

    static class Move extends MaintenanceDeployment.Move {

        final double netSkewReduction;

        Move(Node node, Node fromHost, Node toHost, double netSkewReduction) {
            super(node, fromHost, toHost);
            this.netSkewReduction = netSkewReduction;
        }

        @Override
        public String toString() {
            if (isEmpty()) return "move none";
            return super.toString() + " [skew reduction "  + netSkewReduction + "]";
        }

        public static Move empty() {
            return new Move(null, null, null, 0);
        }

    }

}
