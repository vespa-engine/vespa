// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TransientException;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;

/**
 * Maintains information in the node repo about when this node last responded to ping
 * and fails nodes which have not responded within the given time limit.
 *
 * @author bratseth
 * @author mpolden
 */
public class NodeFailer extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(NodeFailer.class.getName());
    private static final Duration nodeRequestInterval = Duration.ofMinutes(10);

    /** Metric for number of hosts that we want to fail, but cannot due to throttling */
    static final String throttledHostFailuresMetric = "throttledHostFailures";

    /** Metric for number of nodes that we want to fail, but cannot due to throttling */
    static final String throttledNodeFailuresMetric = "throttledNodeFailures";

    /** Metric that indicates whether throttling is active where 1 means active and 0 means inactive */
    static final String throttlingActiveMetric = "nodeFailThrottling";

    private final Deployer deployer;
    private final Duration downTimeLimit;
    private final Orchestrator orchestrator;
    private final Instant constructionTime;
    private final ThrottlePolicy throttlePolicy;
    private final Metric metric;

    public NodeFailer(Deployer deployer, NodeRepository nodeRepository,
                      Duration downTimeLimit, Duration interval, Orchestrator orchestrator,
                      ThrottlePolicy throttlePolicy, Metric metric) {
        // check ping status every interval, but at least twice as often as the down time limit
        super(nodeRepository, min(downTimeLimit.dividedBy(2), interval), metric);
        this.deployer = deployer;
        this.downTimeLimit = downTimeLimit;
        this.orchestrator = orchestrator;
        this.constructionTime = nodeRepository.clock().instant();
        this.throttlePolicy = throttlePolicy;
        this.metric = metric;
    }

    @Override
    protected boolean maintain() {
        if ( ! nodeRepository().nodes().isWorking()) return false;

        int throttledHostFailures = 0;
        int throttledNodeFailures = 0;

        // Ready nodes
        try (Mutex lock = nodeRepository().nodes().lockUnallocated()) {
            for (Map.Entry<Node, String> entry : getReadyNodesByFailureReason().entrySet()) {
                Node node = entry.getKey();
                if (throttle(node)) {
                    if (node.type().isHost()) throttledHostFailures++;
                    else throttledNodeFailures++;
                    continue;
                }
                String reason = entry.getValue();
                nodeRepository().nodes().fail(node.hostname(), Agent.NodeFailer, reason);
            }
        }

        // Active nodes
        for (Map.Entry<Node, String> entry : getActiveNodesByFailureReason().entrySet()) {
            Node node = entry.getKey();
            if (!failAllowedFor(node.type())) continue;

            if (throttle(node)) {
                if (node.type().isHost())
                    throttledHostFailures++;
                else
                    throttledNodeFailures++;

                continue;
            }
            String reason = entry.getValue();
            failActive(node, reason);
        }

        int throttlingActive = Math.min(1, throttledHostFailures + throttledNodeFailures);
        metric.set(throttlingActiveMetric, throttlingActive, null);
        metric.set(throttledHostFailuresMetric, throttledHostFailures, null);
        metric.set(throttledNodeFailuresMetric, throttledNodeFailures, null);
        return throttlingActive == 0;
    }

    private Map<Node, String> getReadyNodesByFailureReason() {
        Instant oldestAcceptableRequestTime =
                // Allow requests some time to be registered in case all config servers have been down
                constructionTime.isAfter(clock().instant().minus(nodeRequestInterval.multipliedBy(2))) ?
                        Instant.EPOCH :

                        // Nodes are taken as dead if they have not made a config request since this instant.
                        // Add 10 minutes to the down time limit to allow nodes to make a request that infrequently.
                        clock().instant().minus(downTimeLimit).minus(nodeRequestInterval);

        Map<Node, String> nodesByFailureReason = new HashMap<>();
        for (Node node : nodeRepository().nodes().list(Node.State.ready)) {
            if (expectConfigRequests(node) && ! hasNodeRequestedConfigAfter(node, oldestAcceptableRequestTime)) {
                nodesByFailureReason.put(node, "Not receiving config requests from node");
            } else {
                Node hostNode = node.parentHostname().flatMap(parent -> nodeRepository().nodes().node(parent)).orElse(node);
                List<String> failureReports = reasonsToFailParentHost(hostNode);
                if (failureReports.size() > 0) {
                    if (hostNode.equals(node)) {
                        nodesByFailureReason.put(node, "Host has failure reports: " + failureReports);
                    } else {
                        nodesByFailureReason.put(node, "Parent (" + hostNode + ") has failure reports: " + failureReports);
                    }
                }
            }
        }
        return nodesByFailureReason;
    }

    private Map<Node, String> getActiveNodesByFailureReason() {
        NodeList activeNodes = nodeRepository().nodes().list(Node.State.active);
        Instant graceTimeEnd = clock().instant().minus(downTimeLimit);
        Map<Node, String> nodesByFailureReason = new HashMap<>();
        for (Node node : activeNodes) {
            if (node.history().hasEventBefore(History.Event.Type.down, graceTimeEnd) && ! applicationSuspended(node)) {
                // Allow a grace period after node re-activation
                if ( ! node.history().hasEventAfter(History.Event.Type.activated, graceTimeEnd))
                    nodesByFailureReason.put(node, "Node has been down longer than " + downTimeLimit);
            }
            else if (hostSuspended(node, activeNodes)) {
                Node hostNode = node.parentHostname().flatMap(parent -> nodeRepository().nodes().node(parent)).orElse(node);
                if (hostNode.type().isHost()) {
                    List<String> failureReports = reasonsToFailParentHost(hostNode);
                    if (failureReports.size() > 0) {
                        if (hostNode.equals(node)) {
                            nodesByFailureReason.put(node, "Host has failure reports: " + failureReports);
                        } else {
                            nodesByFailureReason.put(node, "Parent (" + hostNode + ") has failure reports: " + failureReports);
                        }
                    }
                }
            }
        }
        return nodesByFailureReason;
    }

    public static List<String> reasonsToFailParentHost(Node hostNode) {
        return hostNode.reports().getReports().stream()
                .filter(report -> report.getType().hostShouldBeFailed())
                // The generated string is built from the report's ID, created time, and description only.
                .map(report -> report.getReportId() + " reported " + report.getCreatedTime() + ": " + report.getDescription())
                .collect(Collectors.toList());
    }

    /** Returns whether node has any kind of hardware issue */
    static boolean hasHardwareIssue(Node node, NodeRepository nodeRepository) {
        Node hostNode = node.parentHostname().flatMap(parent -> nodeRepository.nodes().node(parent)).orElse(node);
        return reasonsToFailParentHost(hostNode).size() > 0;
    }

    private boolean expectConfigRequests(Node node) {
        return !node.type().isHost();
    }

    private boolean hasNodeRequestedConfigAfter(Node node, Instant instant) {
        return !wasMadeReadyBefore(node, instant) || hasRecordedRequestAfter(node, instant);
    }

    private boolean wasMadeReadyBefore(Node node, Instant instant) {
        return node.history().hasEventBefore(History.Event.Type.readied, instant);
    }

    private boolean hasRecordedRequestAfter(Node node, Instant instant) {
        return node.history().hasEventAfter(History.Event.Type.requested, instant);
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

    private boolean nodeSuspended(Node node) {
        try {
            return orchestrator.getNodeStatus(new HostName(node.hostname())).isSuspended();
        } catch (HostNameNotFoundException e) {
            // Treat it as not suspended
            return false;
        }
    }

    /** Is the node and all active children suspended? */
    private boolean hostSuspended(Node node, NodeList activeNodes) {
        if (!nodeSuspended(node)) return false;
        if (node.parentHostname().isPresent()) return true; // optimization
        return activeNodes.stream()
                .filter(childNode -> childNode.parentHostname().isPresent() &&
                        childNode.parentHostname().get().equals(node.hostname()))
                .allMatch(this::nodeSuspended);
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
    private boolean failActive(Node node, String reason) {
        Optional<Deployment> deployment =
            deployer.deployFromLocalActive(node.allocation().get().owner(), Duration.ofMinutes(30));
        if (deployment.isEmpty()) return false;

        try (Mutex lock = nodeRepository().nodes().lock(node.allocation().get().owner())) {
            // If the active node that we are trying to fail is of type host, we need to successfully fail all
            // the children nodes running on it before we fail the host
            boolean allTenantNodesFailedOutSuccessfully = true;
            String reasonForChildFailure = "Failing due to parent host " + node.hostname() + " failure: " + reason;
            for (Node failingTenantNode : nodeRepository().nodes().list().childrenOf(node)) {
                if (failingTenantNode.state() == Node.State.active) {
                    allTenantNodesFailedOutSuccessfully &= failActive(failingTenantNode, reasonForChildFailure);
                } else {
                    nodeRepository().nodes().fail(failingTenantNode.hostname(), Agent.NodeFailer, reasonForChildFailure);
                }
            }

            if (! allTenantNodesFailedOutSuccessfully) return false;
            node = nodeRepository().nodes().fail(node.hostname(), Agent.NodeFailer, reason);
            try {
                deployment.get().activate();
                return true;
            } catch (TransientException e) {
                log.log(Level.INFO, "Failed to redeploy " + node.allocation().get().owner() +
                                    " with a transient error, will be retried by application maintainer: " +
                                    Exceptions.toMessageString(e));
                return true;
            } catch (RuntimeException e) {
                // The expected reason for deployment to fail here is that there is no capacity available to redeploy.
                // In that case we should leave the node in the active state to avoid failing additional nodes.
                nodeRepository().nodes().reactivate(node.hostname(), Agent.NodeFailer,
                                                    "Failed to redeploy after being failed by NodeFailer");
                log.log(Level.WARNING, "Attempted to fail " + node + " for " + node.allocation().get().owner() +
                                       ", but redeploying without the node failed", e);
                return false;
            }
        }
    }

    /** Returns true if node failing should be throttled */
    private boolean throttle(Node node) {
        if (throttlePolicy == ThrottlePolicy.disabled) return false;
        Instant startOfThrottleWindow = clock().instant().minus(throttlePolicy.throttleWindow);
        NodeList nodes = nodeRepository().nodes().list();
        NodeList recentlyFailedNodes = nodes.stream()
                                            .filter(n -> n.state() == Node.State.failed)
                                            .filter(n -> n.history().hasEventAfter(History.Event.Type.failed, startOfThrottleWindow))
                                            .collect(collectingAndThen(Collectors.toList(), NodeList::copyOf));

        // Allow failing nodes within policy
        if (recentlyFailedNodes.size() < throttlePolicy.allowedToFailOf(nodes.size())) return false;

        // Always allow failing physical nodes up to minimum limit
        if (node.parentHostname().isEmpty() &&
            recentlyFailedNodes.parents().size() < throttlePolicy.minimumAllowedToFail) return false;

        log.info(String.format("Want to fail node %s, but throttling is in effect: %s", node.hostname(),
                               throttlePolicy.toHumanReadableString(nodes.size())));

        return true;
    }

    public enum ThrottlePolicy {

        hosted(Duration.ofDays(1), 0.02, 2),
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

}
