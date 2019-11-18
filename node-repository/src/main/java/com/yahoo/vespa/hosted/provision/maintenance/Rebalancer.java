// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TransientException;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.core.Main;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.DockerHostCapacity;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.NodePrioritizer;
import com.yahoo.yolean.Exceptions;

import java.io.Closeable;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public class Rebalancer extends Maintainer {

    private final Deployer deployer;
    private final HostResourcesCalculator hostResourcesCalculator;
    private final Optional<HostProvisioner> hostProvisioner;
    private final Metric metric;
    private final Clock clock;

    public Rebalancer(Deployer deployer,
                      NodeRepository nodeRepository,
                      HostResourcesCalculator hostResourcesCalculator,
                      Optional<HostProvisioner> hostProvisioner,
                      Metric metric,
                      Clock clock,
                      Duration interval) {
        super(nodeRepository, interval);
        this.deployer = deployer;
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
        deployTo(bestMove);
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
                    bestMove = new Move(node, toHost, netSkewReduction);
            }
        }
        return bestMove;
    }

    /** Returns true only if this operation changes the state of the wantToRetire flag */
    private boolean markWantToRetire(Node node, boolean wantToRetire) {
        try (Mutex lock = nodeRepository().lock(node)) {
            Optional<Node> nodeToMove = nodeRepository().getNode(node.hostname());
            if (nodeToMove.isEmpty()) return false;
            if (nodeToMove.get().state() != Node.State.active) return false;

            if (node.status().wantToRetire() == wantToRetire) return false;

            nodeRepository().write(nodeToMove.get().withWantToRetire(wantToRetire, Agent.system, clock.instant()), lock);
            return true;
        }
    }

    /**
     * Try a redeployment to effect the chosen move.
     * If it can be done, that's ok; we'll try this or another move later.
     *
     * @return true if the move was done, false if it couldn't be
     */
    private boolean deployTo(Move move) {
        ApplicationId application = move.node.allocation().get().owner();
        try (MaintenanceDeployment deployment = new MaintenanceDeployment(application, deployer, nodeRepository())) {
            if ( ! deployment.isValid()) return false;

            boolean couldMarkRetiredNow = markWantToRetire(move.node, true);
            if ( ! couldMarkRetiredNow) return false;

            try {
                if ( ! deployment.prepare()) return false;
                if (nodeRepository().getNodes(application, Node.State.reserved).stream().noneMatch(node -> node.hasParent(move.toHost.hostname())))
                    return false; // Deployment is not moving the from node to the target we identified for some reason
                if ( ! deployment.activate()) return false;

                log.info("Rebalancer redeployed " + application + " to " + move);
                return true;
            }
            finally {
                markWantToRetire(move.node, false); // Necessary if this failed, no-op otherwise
            }
        }
    }

    private double skewReductionByRemoving(Node node, Node fromHost, DockerHostCapacity capacity) {
        NodeResources freeHostCapacity = capacity.freeCapacityOf(fromHost);
        double skewBefore = Node.skew(fromHost.flavor().resources(), freeHostCapacity);
        double skewAfter = Node.skew(fromHost.flavor().resources(), freeHostCapacity.add(node.flavor().resources().justNumbers()));
        return skewBefore - skewAfter;
    }

    private double skewReductionByAdding(Node node, Node toHost, DockerHostCapacity capacity) {
        NodeResources freeHostCapacity = capacity.freeCapacityOf(toHost);
        double skewBefore = Node.skew(toHost.flavor().resources(), freeHostCapacity);
        double skewAfter = Node.skew(toHost.flavor().resources(), freeHostCapacity.subtract(node.flavor().resources().justNumbers()));
        return skewBefore - skewAfter;
    }

    private static class Move {

        static final Move none = new Move(null, null, 0);

        final Node node;
        final Node toHost;
        final double netSkewReduction;

        Move(Node node, Node toHost, double netSkewReduction) {
            this.node = node;
            this.toHost = toHost;
            this.netSkewReduction = netSkewReduction;
        }

        @Override
        public String toString() {
            return "move " +
                   ( node == null ? "none" :
                                    (node.hostname() + " to " + toHost + " [skew reduction "  + netSkewReduction + "]"));
        }

    }

    private static class MaintenanceDeployment implements Closeable {

        private static final Logger log = Logger.getLogger(MaintenanceDeployment.class.getName());

        private final ApplicationId application;
        private final Optional<Mutex> lock;
        private final Optional<Deployment> deployment;

        public MaintenanceDeployment(ApplicationId application, Deployer deployer, NodeRepository nodeRepository) {
            this.application = application;
            lock = tryLock(application, nodeRepository);
            deployment = tryDeployment(lock, application, deployer, nodeRepository);
        }

        /** Return whether this is - as yet - functional and can be used to carry out the deployment */
        public boolean isValid() {
            return deployment.isPresent();
        }

        private Optional<Mutex> tryLock(ApplicationId application, NodeRepository nodeRepository) {
            try {
                // Use a short lock to avoid interfering with change deployments
                return Optional.of(nodeRepository.lock(application, Duration.ofSeconds(1)));
            }
            catch (ApplicationLockException e) {
                return Optional.empty();
            }
        }

        private Optional<Deployment> tryDeployment(Optional<Mutex> lock,
                                                   ApplicationId application,
                                                   Deployer deployer,
                                                   NodeRepository nodeRepository) {
            if (lock.isEmpty()) return Optional.empty();
            if (nodeRepository.getNodes(application, Node.State.active).isEmpty()) return Optional.empty();
            return deployer.deployFromLocalActive(application);
        }

        public boolean prepare() {
            return doStep(() -> deployment.get().prepare());
        }

        public boolean activate() {
            return doStep(() -> deployment.get().activate());
        }

        private boolean doStep(Runnable action) {
            if ( ! isValid()) return false;
            try {
                action.run();
                return true;
            } catch (TransientException e) {
                log.log(LogLevel.INFO, "Failed to deploy " + application + " with a transient error: " +
                                       Exceptions.toMessageString(e));
                return false;
            } catch (RuntimeException e) {
                log.log(LogLevel.WARNING, "Exception on maintenance deploy of " + application, e);
                return false;
            }
        }

        @Override
        public void close() {
            lock.ifPresent(l -> l.close());
        }

    }

}
