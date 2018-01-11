// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;

/**
 * Maintains information in the node repo about when this node last responded to ping
 * and fails nodes which have not responded within the given time limit.
 *
 * @author bratseth
 * @author mpolden
 */
public class NodeFailer extends Maintainer {

    private static final Logger log = Logger.getLogger(NodeFailer.class.getName());
    private static final Duration nodeRequestInterval = Duration.ofMinutes(10);

    /** Provides information about the status of ready hosts */
    private final HostLivenessTracker hostLivenessTracker;

    /** Provides (more accurate) information about the status of active hosts */
    private final ServiceMonitor serviceMonitor;

    private final Deployer deployer;
    private final Duration downTimeLimit;
    private final Clock clock;
    private final Orchestrator orchestrator;
    private final Instant constructionTime;
    private final ThrottlePolicy throttlePolicy;

    public NodeFailer(Deployer deployer, HostLivenessTracker hostLivenessTracker,
                      ServiceMonitor serviceMonitor, NodeRepository nodeRepository,
                      Duration downTimeLimit, Clock clock, Orchestrator orchestrator,
                      ThrottlePolicy throttlePolicy,
                      JobControl jobControl) {
        // check ping status every five minutes, but at least twice as often as the down time limit
        super(nodeRepository, min(downTimeLimit.dividedBy(2), Duration.ofMinutes(5)), jobControl);
        this.deployer = deployer;
        this.hostLivenessTracker = hostLivenessTracker;
        this.serviceMonitor = serviceMonitor;
        this.downTimeLimit = downTimeLimit;
        this.clock = clock;
        this.orchestrator = orchestrator;
        this.constructionTime = clock.instant();
        this.throttlePolicy = throttlePolicy;
    }

    @Override
    protected void maintain() {
        // Ready nodes
        try (Mutex lock = nodeRepository().lockUnallocated()) {
            updateNodeLivenessEventsForReadyNodes();

            getReadyNodesByFailureReason().forEach((node, reason) -> {
                if (!throttle(node)) {
                    nodeRepository().fail(node.hostname(), Agent.system, reason);
                }
            });
        }

        // Active nodes
        for (Node node : determineActiveNodeDownStatus()) {
            Instant graceTimeEnd = node.history().event(History.Event.Type.down).get().at().plus(downTimeLimit);
            if (graceTimeEnd.isBefore(clock.instant()) && ! applicationSuspended(node) && failAllowedFor(node.type()))
                if (!throttle(node)) failActive(node, "Node has been down longer than " + downTimeLimit);
        }
    }

    private void updateNodeLivenessEventsForReadyNodes() {
        // Update node last request events through ZooKeeper to collect request to all config servers.
        // We do this here ("lazily") to avoid writing to zk for each config request.
        for (Node node : nodeRepository().getNodes(Node.State.ready)) {
            Optional<Instant> lastLocalRequest = hostLivenessTracker.lastRequestFrom(node.hostname());
            if ( ! lastLocalRequest.isPresent()) continue;

            Optional<History.Event> recordedRequest = node.history().event(History.Event.Type.requested);
            if ( ! recordedRequest.isPresent() || recordedRequest.get().at().isBefore(lastLocalRequest.get())) {
                History updatedHistory = node.history().with(new History.Event(History.Event.Type.requested,
                        Agent.system,
                        lastLocalRequest.get()));
                nodeRepository().write(node.with(updatedHistory));
            }
        }
    }

    private Map<Node, String> getReadyNodesByFailureReason() {
        Instant oldestAcceptableRequestTime =
                // Allow requests some time to be registered in case all config servers have been down
                constructionTime.isAfter(clock.instant().minus(nodeRequestInterval.multipliedBy(2))) ?
                        Instant.EPOCH :

                        // Nodes are taken as dead if they have not made a config request since this instant.
                        // Add 10 minutes to the down time limit to allow nodes to make a request that infrequently.
                        clock.instant().minus(downTimeLimit).minus(nodeRequestInterval);

        Map<Node, String> nodesByFailureReason = new HashMap<>();
        for (Node node : nodeRepository().getNodes(Node.State.ready)) {
            if (! hasNodeRequestedConfigAfter(node, oldestAcceptableRequestTime)) {
                nodesByFailureReason.put(node, "Not receiving config requests from node");
            } else if (node.status().hardwareFailureDescription().isPresent()) {
                nodesByFailureReason.put(node, "Node has hardware failure");
            } else if (node.status().hardwareDivergence().isPresent()) {
                nodesByFailureReason.put(node, "Node has hardware divergence");
            }
        }
        return nodesByFailureReason;
    }

    private boolean hasNodeRequestedConfigAfter(Node node, Instant instant) {
        return !wasMadeReadyBefore(node, instant) || hasRecordedRequestAfter(node, instant);
    }

    private boolean wasMadeReadyBefore(Node node, Instant instant) {
        Optional<History.Event> readiedEvent = node.history().event(History.Event.Type.readied);
        return readiedEvent.map(event -> event.at().isBefore(instant)).orElse(false);
    }

    private boolean hasRecordedRequestAfter(Node node, Instant instant) {
        Optional<History.Event> lastRequest = node.history().event(History.Event.Type.requested);
        return lastRequest.map(event -> event.at().isAfter(instant)).orElse(false);
    }

    private boolean applicationSuspended(Node node) {
        try {
            return orchestrator.getApplicationInstanceStatus(node.allocation().get().owner())
                   == ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN;
        } catch (ApplicationIdNotFoundException e) {
            //Treat it as not suspended and allow to fail the node anyway
            return false;
        }
    }

    /**
     * We can attempt to fail any number of *tenant* and *host* nodes because the operation will not be effected
     * unless the node is replaced.
     * However, nodes of other types are not replaced (because all of the type are used by a single application),
     * so we only allow one to be in failed at any point in time to protect against runaway failing.
     */
    private boolean failAllowedFor(NodeType nodeType) {
        if (nodeType == NodeType.tenant || nodeType == NodeType.host) return true;
        return nodeRepository().getNodes(nodeType, Node.State.failed).size() == 0;
    }

    /**
     * Returns true if the node is considered bad: all monitored services services are down.
     * If a node remains bad for a long time, the NodeFailer will eventually try to fail the node.
     */
    public static boolean badNode(List<ServiceInstance> services) {
        Map<ServiceStatus, Long> countsByStatus = services.stream()
                .collect(Collectors.groupingBy(ServiceInstance::serviceStatus, counting()));

        return countsByStatus.getOrDefault(ServiceStatus.UP, 0L) <= 0L &&
                countsByStatus.getOrDefault(ServiceStatus.DOWN, 0L) > 0L;
    }

    /**
     * If the node is down (see badNode()), and there is no "down" history record, we add it.
     * Otherwise we remove any "down" history record.
     *
     * @return a list of all nodes that should be considered as down
     */
    private List<Node> determineActiveNodeDownStatus() {
        List<Node> downNodes = new ArrayList<>();
        serviceMonitor.getServiceModelSnapshot().getServiceInstancesByHostName()
                .entrySet().stream().forEach(
                        entry -> {
                            Optional<Node> node = nodeRepository().getNode(entry.getKey().s(), Node.State.active);
                            if (node.isPresent()) {
                                if (badNode(entry.getValue())) {
                                    downNodes.add(recordAsDown(node.get()));
                                } else {
                                    clearDownRecord(node.get());
                                }
                            }
                        }
        );

        return downNodes;
    }

    /**
     * Record a node as down if not already recorded and returns the node in the new state.
     * This assumes the node is found in the node
     * repo and that the node is allocated. If we get here otherwise something is truly odd.
     */
    private Node recordAsDown(Node node) {
        if (node.history().event(History.Event.Type.down).isPresent()) return node; // already down: Don't change down timestamp

        try (Mutex lock = nodeRepository().lock(node.allocation().get().owner())) {
            node = nodeRepository().getNode(node.hostname(), Node.State.active).get(); // re-get inside lock
            return nodeRepository().write(node.downAt(clock.instant()));
        }
    }

    private void clearDownRecord(Node node) {
        if ( ! node.history().event(History.Event.Type.down).isPresent()) return;

        try (Mutex lock = nodeRepository().lock(node.allocation().get().owner())) {
            node = nodeRepository().getNode(node.hostname(), Node.State.active).get(); // re-get inside lock
            nodeRepository().write(node.up());
        }
    }

    /**
     * Called when a node should be moved to the failed state: Do that if it seems safe,
     * which is when the node repo has available capacity to replace the node (and all its tenant nodes if host).
     * Otherwise not replacing the node ensures (by Orchestrator check) that no further action will be taken.
     *
     * @return whether node was successfully failed
     */
    private boolean failActive(Node node, String reason) {
        Optional<Deployment> deployment =
            deployer.deployFromLocalActive(node.allocation().get().owner(), Duration.ofMinutes(30));
        if ( ! deployment.isPresent()) return false; // this will be done at another config server

        try (Mutex lock = nodeRepository().lock(node.allocation().get().owner())) {
            // If the active node that we are trying to fail is of type host, we need to successfully fail all
            // the children nodes running on it before we fail the host
            boolean allTenantNodesFailedOutSuccessfully = true;
            for (Node failingTenantNode : nodeRepository().getChildNodes(node.hostname())) {
                if (failingTenantNode.state() == Node.State.active) {
                    allTenantNodesFailedOutSuccessfully &= failActive(failingTenantNode, reason);
                } else {
                    nodeRepository().fail(failingTenantNode.hostname(), Agent.system, reason);
                }
            }

            if (! allTenantNodesFailedOutSuccessfully) return false;
            node = nodeRepository().fail(node.hostname(), Agent.system, reason);
            try {
                deployment.get().activate();
                return true;
            }
            catch (RuntimeException e) {
                // The expected reason for deployment to fail here is that there is no capacity available to redeploy.
                // In that case we should leave the node in the active state to avoid failing additional nodes.
                nodeRepository().reactivate(node.hostname(), Agent.system);
                log.log(Level.WARNING, "Attempted to fail " + node + " for " + node.allocation().get().owner() +
                                       ", but redeploying without the node failed", e);
                return false;
            }
        }
    }

    /** Returns true if node failing should be throttled */
    private boolean throttle(Node node) {
        if (throttlePolicy == ThrottlePolicy.disabled) return false;
        Instant startOfThrottleWindow = clock.instant().minus(throttlePolicy.throttleWindow);
        List<Node> nodes = nodeRepository().getNodes();
        long recentlyFailedNodes = nodes.stream()
                .map(n -> n.history().event(History.Event.Type.failed))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(failedEvent -> failedEvent.at().isAfter(startOfThrottleWindow))
                .count();
        int allowedFailedNodes = (int) Math.max(nodes.size() * throttlePolicy.fractionAllowedToFail,
                throttlePolicy.minimumAllowedToFail);

        boolean throttle = allowedFailedNodes < recentlyFailedNodes ||
                (allowedFailedNodes == recentlyFailedNodes && node.type() != NodeType.host);
        if (throttle) {
            log.info(String.format("Want to fail node %s, but throttling is in effect: %s", node.hostname(),
                                   throttlePolicy.toHumanReadableString()));
        }
        return throttle;
    }

    public enum ThrottlePolicy {

        hosted(Duration.ofDays(1), 0.01, 2),
        disabled(Duration.ZERO, 0, 0);

        public final Duration throttleWindow;
        public final double fractionAllowedToFail;
        public final int minimumAllowedToFail;

        ThrottlePolicy(Duration throttleWindow, double fractionAllowedToFail, int minimumAllowedToFail) {
            this.throttleWindow = throttleWindow;
            this.fractionAllowedToFail = fractionAllowedToFail;
            this.minimumAllowedToFail = minimumAllowedToFail;
        }

        public String toHumanReadableString() {
            return String.format("Max %.0f%% or %d nodes can fail over a period of %s", fractionAllowedToFail*100,
                                 minimumAllowedToFail, throttleWindow);
        }
    }

}
