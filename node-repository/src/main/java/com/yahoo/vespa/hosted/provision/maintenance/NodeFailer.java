// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TransientException;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Maintains information in the node repo about when this node last responded to ping
 * and fails nodes which have not responded within the given time limit.
 *
 * @author bratseth
 * @author mpolden
 */
public class NodeFailer extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(NodeFailer.class.getName());

    /** Metric for number of hosts that we want to fail, but cannot due to throttling */
    static final String throttledHostFailuresMetric = "throttledHostFailures";

    /** Metric for number of nodes that we want to fail, but cannot due to throttling */
    static final String throttledNodeFailuresMetric = "throttledNodeFailures";

    /** Metric that indicates whether throttling is active where 1 means active and 0 means inactive */
    static final String throttlingActiveMetric = "nodeFailThrottling";

    private final Deployer deployer;
    private final Duration downTimeLimit;
    private final Duration suspendedDownTimeLimit;
    private final ThrottlePolicy throttlePolicy;
    private final Metric metric;

    public NodeFailer(Deployer deployer, NodeRepository nodeRepository,
                      Duration downTimeLimit, Duration interval, ThrottlePolicy throttlePolicy, Metric metric) {
        // check ping status every interval, but at least twice as often as the down time limit
        super(nodeRepository, min(downTimeLimit.dividedBy(2), interval), metric);
        this.deployer = deployer;
        this.downTimeLimit = downTimeLimit;
        this.suspendedDownTimeLimit = downTimeLimit.multipliedBy(4); // Allow more downtime when a node is suspended
        this.throttlePolicy = throttlePolicy;
        this.metric = metric;
    }

    @Override
    protected double maintain() {
        if ( ! nodeRepository().nodes().isWorking()) return 0.0;

        int attempts = 0;
        int failures = 0;
        int throttledHostFailures = 0;
        int throttledNodeFailures = 0;

        // Ready nodes
        try (Mutex lock = nodeRepository().nodes().lockUnallocated()) {
            for (FailingNode failing : findReadyFailingNodes()) {
                attempts++;
                if (throttle(failing.node())) {
                    failures++;
                    if (failing.node().type().isHost())
                        throttledHostFailures++;
                    else
                        throttledNodeFailures++;
                    continue;
                }
                nodeRepository().nodes().fail(failing.node().hostname(), Agent.NodeFailer, failing.reason());
            }
        }

        // Active nodes
        for (FailingNode failing : findActiveFailingNodes()) {
            attempts++;
            if (!failAllowedFor(failing.node().type())) continue;

            if (throttle(failing.node())) {
                failures++;
                if (failing.node().type().isHost())
                    throttledHostFailures++;
                else
                    throttledNodeFailures++;
                continue;
            }
            failActive(failing);
        }

        // Active hosts
        NodeList activeNodes = nodeRepository().nodes().list(Node.State.active);
        for (Node host : activeNodes.hosts().failing()) {
            if ( ! activeNodes.childrenOf(host).isEmpty()) continue;
            Optional<NodeMutex> locked = Optional.empty();
            try {
                attempts++;
                locked = nodeRepository().nodes().lockAndGet(host);
                if (locked.isEmpty()) continue;
                nodeRepository().nodes().fail(List.of(locked.get().node()), Agent.NodeFailer,
                                              "Host should be failed and have no tenant nodes");
            }
            catch (Exception e) {
                failures++;
            }
            finally {
                locked.ifPresent(NodeMutex::close);
            }
        }

        int throttlingActive = Math.min(1, throttledHostFailures + throttledNodeFailures);
        metric.set(throttlingActiveMetric, throttlingActive, null);
        metric.set(throttledHostFailuresMetric, throttledHostFailures, null);
        metric.set(throttledNodeFailuresMetric, throttledNodeFailures, null);
        return asSuccessFactor(attempts, failures);
    }

    private Collection<FailingNode> findReadyFailingNodes() {
        Set<FailingNode> failingNodes = new HashSet<>();
        for (Node node : nodeRepository().nodes().list(Node.State.ready)) {
            Node hostNode = node.parentHostname().flatMap(parent -> nodeRepository().nodes().node(parent)).orElse(node);
            List<String> failureReports = reasonsToFailHost(hostNode);
            if (failureReports.size() > 0) {
                if (hostNode.equals(node)) {
                    failingNodes.add(new FailingNode(node, "Host has failure reports: " + failureReports));
                } else {
                    failingNodes.add(new FailingNode(node, "Parent (" + hostNode + ") has failure reports: " + failureReports));
                }
            }
        }
        return failingNodes;
    }

    private Collection<FailingNode> findActiveFailingNodes() {
        Set<FailingNode> failingNodes = new HashSet<>();
        NodeList activeNodes = nodeRepository().nodes().list(Node.State.active);

        for (Node node : activeNodes) {
            Instant graceTimeStart = clock().instant().minus(nodeRepository().nodes().suspended(node) ? suspendedDownTimeLimit : downTimeLimit);
            if (node.isDown() && node.history().hasEventBefore(History.Event.Type.down, graceTimeStart) && !applicationSuspended(node)) {
                // Allow a grace period after node re-activation
                if (!node.history().hasEventAfter(History.Event.Type.activated, graceTimeStart))
                    failingNodes.add(new FailingNode(node, "Node has been down longer than " + downTimeLimit));
            }
        }

        for (Node node : activeNodes) {
            if (allSuspended(node, activeNodes)) {
                Node host = node.parentHostname().flatMap(parent -> nodeRepository().nodes().node(parent)).orElse(node);
                if (host.type().isHost()) {
                    List<String> failureReports = reasonsToFailHost(host);
                    if ( ! failureReports.isEmpty()) {
                        failingNodes.add(new FailingNode(node, host.equals(node) ?
                                                               "Host has failure reports: " + failureReports :
                                                               "Parent " + host + " has failure reports: " + failureReports));
                    }
                }
            }
        }

        return failingNodes;
    }

    public static List<String> reasonsToFailHost(Node host) {
        return host.reports().getReports().stream()
                .filter(report -> report.getType().hostShouldBeFailed())
                // The generated string is built from the report's ID, created time, and description only.
                .map(report -> report.getReportId() + " reported " + report.getCreatedTime() + ": " + report.getDescription())
                .collect(Collectors.toList());
    }

    /** Returns whether node has any kind of hardware issue */
    static boolean hasHardwareIssue(Node node, NodeRepository nodeRepository) {
        Node host = node.parentHostname().flatMap(parent -> nodeRepository.nodes().node(parent)).orElse(node);
        return reasonsToFailHost(host).size() > 0;
    }

    private boolean applicationSuspended(Node node) {
        try {
            return nodeRepository().orchestrator().getApplicationInstanceStatus(node.allocation().get().owner())
                   == ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN;
        } catch (ApplicationIdNotFoundException e) {
            // Treat it as not suspended and allow to fail the node anyway
            return false;
        }
    }

    /** Is the node and all active children suspended? */
    private boolean allSuspended(Node node, NodeList activeNodes) {
        if (!nodeRepository().nodes().suspended(node)) return false;
        if (node.parentHostname().isPresent()) return true; // optimization
        return activeNodes.stream()
                .filter(childNode -> childNode.parentHostname().isPresent() &&
                        childNode.parentHostname().get().equals(node.hostname()))
                .allMatch(nodeRepository().nodes()::suspended);
    }

    /**
     * We can attempt to fail any number of *tenant* and *host* nodes because the operation will not be effected
     * unless the node is replaced.
     * We can also attempt to fail a single proxy(host) as there should be enough redundancy to handle that.
     * But we refuse to fail out config(host)/controller(host)
     */
    private boolean failAllowedFor(NodeType nodeType) {
        switch (nodeType) {
            case tenant:
            case host:
                return true;
            case proxy:
            case proxyhost:
                return nodeRepository().nodes().list(Node.State.failed).nodeType(nodeType).isEmpty();
            default:
                return false;
        }
    }

    /**
     * Called when a node should be moved to the failed state: Do that if it seems safe,
     * which is when the node repo has available capacity to replace the node (and all its tenant nodes if host).
     * Otherwise not replacing the node ensures (by Orchestrator check) that no further action will be taken.
     *
     * @return whether node was successfully failed
     */
    private boolean failActive(FailingNode failing) {
        Optional<Deployment> deployment =
            deployer.deployFromLocalActive(failing.node().allocation().get().owner(), Duration.ofMinutes(30));
        if (deployment.isEmpty()) return false;

        try (Mutex lock = nodeRepository().nodes().lock(failing.node().allocation().get().owner())) {
            // If the active node that we are trying to fail is of type host, we need to successfully fail all
            // the children nodes running on it before we fail the host
            boolean allTenantNodesFailedOutSuccessfully = true;
            String reasonForChildFailure = "Failing due to parent host " + failing.node().hostname() + " failure: " + failing.reason();
            for (Node failingTenantNode : nodeRepository().nodes().list().childrenOf(failing.node())) {
                if (failingTenantNode.state() == Node.State.active) {
                    allTenantNodesFailedOutSuccessfully &= failActive(new FailingNode(failingTenantNode, reasonForChildFailure));
                } else {
                    nodeRepository().nodes().fail(failingTenantNode.hostname(), Agent.NodeFailer, reasonForChildFailure);
                }
            }

            if (! allTenantNodesFailedOutSuccessfully) return false;
            wantToFail(failing.node(), true, lock);
            try {
                deployment.get().activate();
                return true;
            } catch (TransientException e) {
                log.log(Level.INFO, "Failed to redeploy " + failing.node().allocation().get().owner() +
                                    " with a transient error, will be retried by application maintainer: " +
                                    Exceptions.toMessageString(e));
                return true;
            } catch (RuntimeException e) {
                // Reset want to fail: We'll retry failing unless it heals in the meantime
                nodeRepository().nodes().node(failing.node().hostname())
                                        .ifPresent(n -> wantToFail(n, false, lock));
                log.log(Level.WARNING, "Could not fail " + failing.node() + " for " + failing.node().allocation().get().owner() +
                                       " for " + failing.reason() + ": " + Exceptions.toMessageString(e));
                return false;
            }
        }
    }

    private void wantToFail(Node node, boolean wantToFail, Mutex lock) {
        nodeRepository().nodes().write(node.withWantToFail(wantToFail, Agent.NodeFailer, clock().instant()), lock);
    }

    /** Returns true if node failing should be throttled */
    private boolean throttle(Node node) {
        if (throttlePolicy == ThrottlePolicy.disabled) return false;
        Instant startOfThrottleWindow = clock().instant().minus(throttlePolicy.throttleWindow);
        NodeList allNodes = nodeRepository().nodes().list();
        NodeList recentlyFailedNodes = allNodes.state(Node.State.failed)
                                               .matching(n -> n.history().hasEventAfter(History.Event.Type.failed,
                                                                                        startOfThrottleWindow));

        // Allow failing any node within policy
        if (recentlyFailedNodes.size() < throttlePolicy.allowedToFailOf(allNodes.size())) return false;

        // Always allow failing a minimum number of hosts
        if (node.parentHostname().isEmpty() &&
            recentlyFailedNodes.parents().size() < throttlePolicy.minimumAllowedToFail) return false;

        // Always allow failing children of a failed host
        if (recentlyFailedNodes.parentOf(node).isPresent()) return false;

        log.info(String.format("Want to fail node %s, but throttling is in effect: %s", node.hostname(),
                               throttlePolicy.toHumanReadableString(allNodes.size())));

        return true;
    }

    public enum ThrottlePolicy {

        hosted(Duration.ofDays(1), 0.03, 2),
        disabled(Duration.ZERO, 0, 0);

        private final Duration throttleWindow;
        private final double fractionAllowedToFail;
        private final int minimumAllowedToFail;

        ThrottlePolicy(Duration throttleWindow, double fractionAllowedToFail, int minimumAllowedToFail) {
            this.throttleWindow = throttleWindow;
            this.fractionAllowedToFail = fractionAllowedToFail;
            this.minimumAllowedToFail = minimumAllowedToFail;
        }

        public int allowedToFailOf(int totalNodes) {
            return (int) Math.max(totalNodes * fractionAllowedToFail, minimumAllowedToFail);
        }

        public String toHumanReadableString(int totalNodes) {
            return String.format("Max %.0f%% (%d) or %d nodes can fail over a period of %s", fractionAllowedToFail*100,
                                 allowedToFailOf(totalNodes),
                                 minimumAllowedToFail, throttleWindow);
        }

    }

    private static class FailingNode {

        private final Node node;
        private final String reason;

        public FailingNode(Node node, String reason) {
            this.node = node;
            this.reason = reason;
        }

        public Node node() { return node; }
        public String reason() { return reason; }

        @Override
        public boolean equals(Object other) {
            if ( ! (other instanceof FailingNode)) return false;
            return ((FailingNode)other).node().equals(this.node());
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }

    }

}
