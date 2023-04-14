// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TransientException;
import com.yahoo.jdisc.Metric;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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

        int throttlingActive = Math.min(1, throttledHostFailures + throttledNodeFailures);
        metric.set(throttlingActiveMetric, throttlingActive, null);
        metric.set(throttledHostFailuresMetric, throttledHostFailures, null);
        metric.set(throttledNodeFailuresMetric, throttledNodeFailures, null);
        return asSuccessFactorDeviation(attempts, failures);
    }

    private Collection<FailingNode> findActiveFailingNodes() {
        Set<FailingNode> failingNodes = new HashSet<>();
        NodeList activeNodes = nodeRepository().nodes().list(Node.State.active);

        for (Node host : activeNodes.hosts().failing())
            failingNodes.add(new FailingNode(host, "Host should be failed and have no tenant nodes"));

        for (Node node : activeNodes) {
            Instant graceTimeStart = clock().instant().minus(nodeRepository().nodes().suspended(node) ? suspendedDownTimeLimit : downTimeLimit);
            if (node.isDown() && node.history().hasEventBefore(History.Event.Type.down, graceTimeStart) && !applicationSuspended(node) && !undergoingCmr(node)) {
                // Allow a grace period after node re-activation
                if (!node.history().hasEventAfter(History.Event.Type.activated, graceTimeStart))
                    failingNodes.add(new FailingNode(node, "Node has been down longer than " + downTimeLimit));
            }
        }

        for (Node node : activeNodes) {
            if (allSuspended(node, activeNodes)) {
                Node host = node.parentHostname().flatMap(activeNodes::node).orElse(node);
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
                .toList();
    }

    /** Returns whether node has any kind of hardware issue */
    static boolean hasHardwareIssue(Node node, NodeList allNodes) {
        Node host = node.parentHostname().flatMap(allNodes::node).orElse(node);
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

    private boolean undergoingCmr(Node node) {
        return node.reports().getReport("vcmr")
                .map(report ->
                        SlimeUtils.entriesStream(report.getInspector().field("upcoming"))
                                .anyMatch(cmr -> {
                                    var startTime = cmr.field("plannedStartTime").asLong();
                                    var endTime = cmr.field("plannedEndTime").asLong();
                                    var now = clock().instant().getEpochSecond();
                                    return now > startTime && now < endTime;
                                })
                ).orElse(false);
    }

    /** Is the node and all active children suspended? */
    private boolean allSuspended(Node node, NodeList activeNodes) {
        if (!nodeRepository().nodes().suspended(node)) return false;
        if (node.parentHostname().isPresent()) return true; // optimization
        return activeNodes.childrenOf(node.hostname()).stream().allMatch(nodeRepository().nodes()::suspended);
    }

    /**
     * We can attempt to fail any number of *tenant* and *host* nodes because the operation will not be effected
     * unless the node is replaced.
     * We can also attempt to fail a single proxy(host) as there should be enough redundancy to handle that.
     * But we refuse to fail out config(host)/controller(host)
     */
    private boolean failAllowedFor(NodeType nodeType) {
        return switch (nodeType) {
            case tenant, host -> true;
            case proxy, proxyhost -> nodeRepository().nodes().list(Node.State.failed).nodeType(nodeType).isEmpty();
            default -> false;
        };
    }

    /**
     * Called when a node should be moved to the failed state: Do that if it seems safe,
     * which is when the node repo has available capacity to replace the node (and all its tenant nodes if host).
     * Otherwise not replacing the node ensures (by Orchestrator check) that no further action will be taken.
     */
    private void failActive(FailingNode failing) {
        Optional<Deployment> deployment =
            deployer.deployFromLocalActive(failing.node().allocation().get().owner(), Duration.ofMinutes(5));
        if (deployment.isEmpty()) return;

        // If the active node that we are trying to fail is of type host, we need to successfully fail all
        // the children nodes running on it before we fail the host.  Failing a child node in a dynamically
        // provisioned zone may require provisioning new hosts that require the host application lock to be held,
        // so we must release ours before failing the children.
        List<FailingNode> activeChildrenToFail = new ArrayList<>();
        boolean redeploy = false;
        try (NodeMutex lock = nodeRepository().nodes().lockAndGetRequired(failing.node())) {
            // Now that we have gotten the node object under the proper lock, sanity-check it still makes sense to fail
            if (!Objects.equals(failing.node().allocation().map(Allocation::owner), lock.node().allocation().map(Allocation::owner)))
                return;
            if (lock.node().state() == Node.State.failed)
                return;
            if (!Objects.equals(failing.node().state(), lock.node().state()))
                return;
            failing = new FailingNode(lock.node(), failing.reason);

            String reasonForChildFailure = "Failing due to parent host " + failing.node().hostname() + " failure: " + failing.reason();
            for (Node failingTenantNode : nodeRepository().nodes().list().childrenOf(failing.node())) {
                if (failingTenantNode.state() == Node.State.active) {
                    activeChildrenToFail.add(new FailingNode(failingTenantNode, reasonForChildFailure));
                } else if (failingTenantNode.state() != Node.State.failed) {
                    nodeRepository().nodes().fail(failingTenantNode.hostname(), Agent.NodeFailer, reasonForChildFailure);
                }
            }

            if (activeChildrenToFail.isEmpty()) {
                log.log(Level.INFO, "Failing out " + failing.node + ": " + failing.reason);
                markWantToFail(failing.node(), true, lock);
                redeploy = true;
            }
        }

        // Redeploy to replace failing node
        if (redeploy) {
            redeploy(deployment.get(), failing);
            return;
        }

        // In a dynamically provisioned zone the failing of the first child may require a new host to be provisioned,
        // so failActive() may take a long time to complete, but the remaining children should be fast.
        activeChildrenToFail.forEach(this::failActive);

    }

    private void redeploy(Deployment deployment, FailingNode failing) {
        try {
            deployment.activate();
        } catch (TransientException | UncheckedTimeoutException e) {
            log.log(Level.INFO, "Failed to redeploy " + failing.node().allocation().get().owner() +
                                " with a transient error, will be retried by application maintainer: " +
                                Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            // Reset want to fail: We'll retry failing unless it heals in the meantime
            Optional<NodeMutex> optionalNodeMutex = nodeRepository().nodes().lockAndGet(failing.node());
            if (optionalNodeMutex.isEmpty()) return;
            try (var nodeMutex = optionalNodeMutex.get()) {
                markWantToFail(nodeMutex.node(), false, nodeMutex);
                log.log(Level.WARNING, "Could not fail " + failing.node() + " for " + failing.node().allocation().get().owner() +
                                       " for " + failing.reason() + ": " + Exceptions.toMessageString(e));
            }
        }
    }

    private void markWantToFail(Node node, boolean wantToFail, Mutex lock) {
        if (node.status().wantToFail() != wantToFail) {
            nodeRepository().nodes().write(node.withWantToFail(wantToFail, Agent.NodeFailer, clock().instant()), lock);
        }
    }

    /** Returns true if node failing should be throttled */
    private boolean throttle(Node node) {
        if (throttlePolicy == ThrottlePolicy.disabled) return false;
        Instant startOfThrottleWindow = clock().instant().minus(throttlePolicy.throttleWindow);
        NodeList allNodes = nodeRepository().nodes().list();
        NodeList recentlyFailedNodes = allNodes
                .matching(n -> n.status().wantToFail() ||
                               (n.state() == Node.State.failed &&
                                n.history().hasEventAfter(History.Event.Type.failed, startOfThrottleWindow)));

        // Allow failing any node within policy
        if (recentlyFailedNodes.size() < throttlePolicy.allowedToFailOf(allNodes.size())) return false;

        // Always allow failing a minimum number of hosts
        if (node.parentHostname().isEmpty()) {
            Set<String> parentsOfRecentlyFailedNodes = recentlyFailedNodes.stream()
                                                                          .map(n -> n.parentHostname().orElse(n.hostname()))
                                                                          .collect(Collectors.toSet());
            long potentiallyFailed = parentsOfRecentlyFailedNodes.contains(node.hostname()) ?
                                     parentsOfRecentlyFailedNodes.size() :
                                     parentsOfRecentlyFailedNodes.size() + 1;
            if (potentiallyFailed <= throttlePolicy.minimumAllowedToFail) return false;
        }

        // Always allow failing children of a failed host
        if (recentlyFailedNodes.parentOf(node).isPresent()) return false;

        log.info(String.format("Want to fail node %s, but throttling is in effect: %s", node.hostname(),
                               throttlePolicy.toHumanReadableString(allNodes.size())));

        return true;
    }

    public enum ThrottlePolicy {

        hosted(Duration.ofDays(1), 0.04, 2),
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

    private record FailingNode(Node node, String reason) { }

}
