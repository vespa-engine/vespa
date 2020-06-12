// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.HostCapacity;

import java.time.Clock;
import java.time.Duration;

/**
 * @author bratseth
 */
public class Rebalancer extends NodeRepositoryMaintainer {

    static final Duration waitTimeAfterPreviousDeployment = Duration.ofMinutes(10);

    private final Deployer deployer;
    private final Metric metric;
    private final Clock clock;

    public Rebalancer(Deployer deployer,
                      NodeRepository nodeRepository,
                      Metric metric,
                      Clock clock,
                      Duration interval) {
        super(nodeRepository, interval);
        this.deployer = deployer;
        this.metric = metric;
        this.clock = clock;
    }

    @Override
    protected void maintain() {
        if ( ! nodeRepository().zone().getCloud().allowHostSharing()) return; // Rebalancing not necessary
        if (nodeRepository().zone().environment().isTest()) return; // Short lived deployments; no need to rebalance

        // Work with an unlocked snapshot as this can take a long time and full consistency is not needed
        NodeList allNodes = nodeRepository().list();
        updateSkewMetric(allNodes);
        if ( ! zoneIsStable(allNodes)) return;
        findBestMove(allNodes).execute(Agent.Rebalancer, deployer, metric, nodeRepository());
   }

    /** We do this here rather than in MetricsReporter because it is expensive and frequent updates are unnecessary */
    private void updateSkewMetric(NodeList allNodes) {
        HostCapacity capacity = new HostCapacity(allNodes, nodeRepository().resourcesCalculator());
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
        HostCapacity capacity = new HostCapacity(allNodes, nodeRepository().resourcesCalculator());
        Move bestMove = Move.empty();
        for (Node node : allNodes.nodeType(NodeType.tenant).state(Node.State.active)) {
            if (node.parentHostname().isEmpty()) continue;
            ApplicationId applicationId = node.allocation().get().owner();
            if (applicationId.instance().isTester()) continue;
            if (deployedRecently(applicationId)) continue;
            for (Node toHost : allNodes.matching(nodeRepository()::canAllocateTenantNodeTo)) {
                if (toHost.hostname().equals(node.parentHostname().get())) continue;
                if ( ! capacity.freeCapacityOf(toHost).satisfies(node.resources())) continue;

                double skewReductionAtFromHost = skewReductionByRemoving(node, allNodes.parentOf(node).get(), capacity);
                double skewReductionAtToHost = skewReductionByAdding(node, toHost, capacity);
                double netSkewReduction = skewReductionAtFromHost + skewReductionAtToHost;
                if (netSkewReduction > bestMove.netSkewReduction)
                    bestMove = new Move(node, nodeRepository().getNode(node.parentHostname().get()).get(), toHost, netSkewReduction);
            }
        }
        return bestMove;
    }

    private double skewReductionByRemoving(Node node, Node fromHost, HostCapacity capacity) {
        NodeResources freeHostCapacity = capacity.freeCapacityOf(fromHost);
        double skewBefore = Node.skew(fromHost.flavor().resources(), freeHostCapacity);
        double skewAfter = Node.skew(fromHost.flavor().resources(), freeHostCapacity.add(node.flavor().resources().justNumbers()));
        return skewBefore - skewAfter;
    }

    private double skewReductionByAdding(Node node, Node toHost, HostCapacity capacity) {
        NodeResources freeHostCapacity = capacity.freeCapacityOf(toHost);
        double skewBefore = Node.skew(toHost.flavor().resources(), freeHostCapacity);
        double skewAfter = Node.skew(toHost.flavor().resources(), freeHostCapacity.subtract(node.resources().justNumbers()));
        return skewBefore - skewAfter;
    }

    protected boolean deployedRecently(ApplicationId application) {
        return deployer.lastDeployTime(application)
                .map(lastDeployTime -> lastDeployTime.isAfter(clock.instant().minus(waitTimeAfterPreviousDeployment)))
                // We only know last deploy time for applications that were deployed on this config server,
                // the rest will be deployed on another config server
                .orElse(true);
    }

    private static class Move extends MaintenanceDeployment.Move {

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
