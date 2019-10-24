// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.DockerHostCapacity;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.NodePrioritizer;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Rebalancer extends Maintainer {

    private final HostResourcesCalculator hostResourcesCalculator;
    private final Optional<HostProvisioner> hostProvisioner;
    private final Metric metric;
    private final Clock clock;

    public Rebalancer(NodeRepository nodeRepository,
                      HostResourcesCalculator hostResourcesCalculator,
                      Optional<HostProvisioner> hostProvisioner,
                      Metric metric,
                      Clock clock,
                      Duration interval) {
        super(nodeRepository, interval);
        this.hostResourcesCalculator = hostResourcesCalculator;
        this.hostProvisioner = hostProvisioner;
        this.metric = metric;
        this.clock = clock;
    }

    @Override
    protected void maintain() {
        if (hostProvisioner.isPresent()) return; // All nodes will be allocated on new hosts, so rebalancing makes no sense

        // Work with an unlocked snapshot as this can take a long time and full consistency is not needed
        NodeList allNodes = nodeRepository().list();

        updateSkewMetric(allNodes);

        if ( ! zoneIsStable(allNodes)) return;

        Move bestMove = findBestMove(allNodes);
        if (bestMove == Move.none) return;
        markWantToRetire(bestMove.node);
    }

    /** We do this here rather than in MetricsReporter because it is expensive and frequent updates are unnecessary */
    private void updateSkewMetric(NodeList allNodes) {
        DockerHostCapacity capacity = new DockerHostCapacity(allNodes, hostResourcesCalculator);
        double totalSkew = 0;
        int hostCount = 0;
        for (Node host : allNodes.nodeType((NodeType.host)).state(Node.State.active)) {
            hostCount++;
            totalSkew += Node.skew(host.flavor().resources(), capacity.freeCapacityOf(host));
        }
        metric.set("hostedVespa.docker.skew", totalSkew/hostCount, null);
    }

    private boolean zoneIsStable(NodeList allNodes) {
        NodeList active = allNodes.state(Node.State.active);
        if (active.stream().anyMatch(node -> node.allocation().get().membership().retired())) return false;
        if (active.stream().anyMatch(node -> node.status().wantToRetire())) return false;
        return true;
    }

    /**
     * Find the best move to reduce allocation skew and returns it.
     * Returns Move.none if no moves can be made to reduce skew.
     */
    private Move findBestMove(NodeList allNodes) {
        DockerHostCapacity capacity = new DockerHostCapacity(allNodes, hostResourcesCalculator);
        Move bestMove = Move.none;
        for (Node node : allNodes.nodeType(NodeType.tenant).state(Node.State.active)) {
            if (node.parentHostname().isEmpty()) continue;
            for (Node toHost : allNodes.nodeType(NodeType.host).state(NodePrioritizer.ALLOCATABLE_HOST_STATES)) {
                if (toHost.hostname().equals(node.parentHostname().get())) continue;
                if ( ! capacity.freeCapacityOf(toHost).satisfies(node.flavor().resources())) continue;

                double skewReductionAtFromHost = skewReductionByRemoving(node, allNodes.parentOf(node).get(), capacity);
                double skewReductionAtToHost = skewReductionByAdding(node, toHost, capacity);
                double netSkewReduction = skewReductionAtFromHost + skewReductionAtToHost;
                if (netSkewReduction > bestMove.netSkewReduction)
                    bestMove = new Move(node, netSkewReduction);
            }
        }
        return bestMove;
    }

    private void markWantToRetire(Node node) {
        try (Mutex lock = nodeRepository().lock(node)) {
            Optional<Node> nodeToMove = nodeRepository().getNode(node.hostname());
            if (nodeToMove.isEmpty()) return;
            if (nodeToMove.get().state() != Node.State.active) return;

            nodeRepository().write(nodeToMove.get().withWantToRetire(true, Agent.system, clock.instant()), lock);
            log.info("Marked " + nodeToMove.get() + " as want to retire to reduce allocation skew");
        }
    }

    private double skewReductionByRemoving(Node node, Node fromHost, DockerHostCapacity capacity) {
        NodeResources freeHostCapacity = capacity.freeCapacityOf(fromHost);
        double skewBefore = Node.skew(fromHost.flavor().resources(), freeHostCapacity);
        double skewAfter = Node.skew(fromHost.flavor().resources(), freeHostCapacity.add(node.flavor().resources().anySpeed()));
        return skewBefore - skewAfter;
    }

    private double skewReductionByAdding(Node node, Node toHost, DockerHostCapacity capacity) {
        NodeResources freeHostCapacity = capacity.freeCapacityOf(toHost);
        double skewBefore = Node.skew(toHost.flavor().resources(), freeHostCapacity);
        double skewAfter = Node.skew(toHost.flavor().resources(), freeHostCapacity.subtract(node.flavor().resources().anySpeed()));
        return skewBefore - skewAfter;
    }

    private static class Move {

        static final Move none = new Move(null, 0);

        final Node node;
        final double netSkewReduction;

        Move(Node node, double netSkewReduction) {
            this.node = node;
            this.netSkewReduction = netSkewReduction;
        }

        @Override
        public String toString() {
            return "move: " +
                   ( node == null ? "none" : node.hostname() + ", skew reduction "  + netSkewReduction );
        }

    }

}
