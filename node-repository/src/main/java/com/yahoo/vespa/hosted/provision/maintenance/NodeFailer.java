// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TransientException;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
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

    /** Metric for number of hosts that we want to fail, but cannot due to throttling */
    static final String throttledHostFailuresMetric = "throttledHostFailures";

    /** Metric for number of nodes (docker containers) that we want to fail, but cannot due to throttling */
    static final String throttledNodeFailuresMetric = "throttledNodeFailures";

    /** Metric that indicates whether throttling is active where 1 means active and 0 means inactive */
    static final String throttlingActiveMetric = "nodeFailThrottling";

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
    private final Metric metric;

    public NodeFailer(Deployer deployer, HostLivenessTracker hostLivenessTracker,
                      ServiceMonitor serviceMonitor, NodeRepository nodeRepository,
                      Duration downTimeLimit, Clock clock, Orchestrator orchestrator,
                      ThrottlePolicy throttlePolicy, Metric metric) {
        // check ping status every five minutes, but at least twice as often as the down time limit
        super(nodeRepository, min(downTimeLimit.dividedBy(2), Duration.ofMinutes(5)));
        this.deployer = deployer;
        this.hostLivenessTracker = hostLivenessTracker;
        this.serviceMonitor = serviceMonitor;
        this.downTimeLimit = downTimeLimit;
        this.clock = clock;
        this.orchestrator = orchestrator;
        this.constructionTime = clock.instant();
        this.throttlePolicy = throttlePolicy;
        this.metric = metric;
    }

    @Override
    protected void maintain() {
        int throttledHostFailures = 0;
        int throttledNodeFailures = 0;

        // Ready nodes
        try (Mutex lock = nodeRepository().lockUnallocated()) {
            updateNodeLivenessEventsForReadyNodes(lock);

            for (Map.Entry<Node, String> entry : getReadyNodesByFailureReason().entrySet()) {
                Node node = entry.getKey();
                if (throttle(node)) {
                    if (node.type().isDockerHost()) throttledHostFailures++;
                    else throttledNodeFailures++;
                    continue;
                }
                String reason = entry.getValue();
                nodeRepository().fail(node.hostname(), Agent.NodeFailer, reason);
            }
        }

        updateNodeDownState();
        List<Node> activeNodes = nodeRepository().getNodes(Node.State.active);

        // Fail active nodes
        for (Map.Entry<Node, String> entry : getActiveNodesByFailureReason(activeNodes).entrySet()) {
            Node node = entry.getKey();
            if (!failAllowedFor(node.type())) {
                continue;
            }
            if (throttle(node)) {
                if (node.type().isDockerHost()) throttledHostFailures++;
                else throttledNodeFailures++;
                continue;
            }
            String reason = entry.getValue();
            failActive(node, reason);
        }

        metric.set(throttlingActiveMetric, Math.min( 1, throttledHostFailures + throttledNodeFailures), null);
        metric.set(throttledHostFailuresMetric, throttledHostFailures, null);
        metric.set(throttledNodeFailuresMetric, throttledNodeFailures, null);
    }

    private void updateNodeLivenessEventsForReadyNodes(Mutex lock) {
        // Update node last request events through ZooKeeper to collect request to all config servers.
        // We do this here ("lazily") to avoid writing to zk for each config request.
        for (Node node : nodeRepository().getNodes(Node.State.ready)) {
            Optional<Instant> lastLocalRequest = hostLivenessTracker.lastRequestFrom(node.hostname());
            if ( ! lastLocalRequest.isPresent()) continue;

            if (! node.history().hasEventAfter(History.Event.Type.requested, lastLocalRequest.get())) {
                History updatedHistory = node.history()
                        .with(new History.Event(History.Event.Type.requested, Agent.NodeFailer, lastLocalRequest.get()));
                nodeRepository().write(node.with(updatedHistory), lock);
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
            if (expectConfigRequests(node) && ! hasNodeRequestedConfigAfter(node, oldestAcceptableRequestTime)) {
                nodesByFailureReason.put(node, "Not receiving config requests from node");
            } else {
                Node hostNode = node.parentHostname().flatMap(parent -> nodeRepository().getNode(parent)).orElse(node);
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

    /**
     * If the node is down (see {@link #badNode}), and there is no "down" history record, we add it.
     * Otherwise we remove any "down" history record.
     */
    private void updateNodeDownState() {
        Map<String, Node> activeNodesByHostname = nodeRepository().getNodes(Node.State.active).stream()
                .collect(Collectors.toMap(Node::hostname, node -> node));

        serviceMonitor.getServiceModelSnapshot().getServiceInstancesByHostName()
                .forEach((hostName, serviceInstances) -> {
                    Node node = activeNodesByHostname.get(hostName.s());
                    if (node == null) return;

                    if (badNode(serviceInstances)) {
                        recordAsDown(node);
                    } else {
                        clearDownRecord(node);
                    }
                });
    }

    private Map<Node, String> getActiveNodesByFailureReason(List<Node> activeNodes) {
        Instant graceTimeEnd = clock.instant().minus(downTimeLimit);
        Map<Node, String> nodesByFailureReason = new HashMap<>();
        for (Node node : activeNodes) {
            if (node.history().hasEventBefore(History.Event.Type.down, graceTimeEnd) && ! applicationSuspended(node)) {
                nodesByFailureReason.put(node, "Node has been down longer than " + downTimeLimit);
            } else if (hostSuspended(node, activeNodes)) {
                Node hostNode = node.parentHostname().flatMap(parent -> nodeRepository().getNode(parent)).orElse(node);
                if (hostNode.type().isDockerHost()) {
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
        Node hostNode = node.parentHostname().flatMap(parent -> nodeRepository.getNode(parent)).orElse(node);
        return reasonsToFailParentHost(hostNode).size() > 0;
    }

    private boolean expectConfigRequests(Node node) {
        return !node.type().isDockerHost();
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
            return orchestrator.getNodeStatus(new HostName(node.hostname())) == HostStatus.ALLOWED_TO_BE_DOWN;
        } catch (HostNameNotFoundException e) {
            // Treat it as not suspended
            return false;
        }
    }

    /** Is the node and all active children suspended? */
    private boolean hostSuspended(Node node, List<Node> activeNodes) {
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
                return nodeRepository().getNodes(nodeType, Node.State.failed).size() == 0;
            default:
                return false;
        }
    }

    /**
     * Returns true if the node is considered bad: All monitored services services are down.
     * If a node remains bad for a long time, the NodeFailer will try to fail the node.
     */
    static boolean badNode(List<ServiceInstance> services) {
        Map<ServiceStatus, Long> countsByStatus = services.stream()
                .collect(Collectors.groupingBy(ServiceInstance::serviceStatus, counting()));

        return countsByStatus.getOrDefault(ServiceStatus.UP, 0L) <= 0L &&
               countsByStatus.getOrDefault(ServiceStatus.DOWN, 0L) > 0L;
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
            return nodeRepository().write(node.downAt(clock.instant()), lock);
        }
    }

    private void clearDownRecord(Node node) {
        if ( ! node.history().event(History.Event.Type.down).isPresent()) return;

        try (Mutex lock = nodeRepository().lock(node.allocation().get().owner())) {
            node = nodeRepository().getNode(node.hostname(), Node.State.active).get(); // re-get inside lock
            nodeRepository().write(node.up(), lock);
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
            String reasonForChildFailure = "Failing due to parent host " + node.hostname() + " failure: " + reason;
            for (Node failingTenantNode : nodeRepository().list().childrenOf(node)) {
                if (failingTenantNode.state() == Node.State.active) {
                    allTenantNodesFailedOutSuccessfully &= failActive(failingTenantNode, reasonForChildFailure);
                } else {
                    nodeRepository().fail(failingTenantNode.hostname(), Agent.NodeFailer, reasonForChildFailure);
                }
            }

            if (! allTenantNodesFailedOutSuccessfully) return false;
            node = nodeRepository().fail(node.hostname(), Agent.NodeFailer, reason);
            try {
                deployment.get().activate();
                return true;
            } catch (TransientException e) {
                log.log(LogLevel.INFO, "Failed to redeploy " + node.allocation().get().owner() +
                        " with a transient error, will be retried by application maintainer: " + Exceptions.toMessageString(e));
                return true;
            } catch (RuntimeException e) {
                // The expected reason for deployment to fail here is that there is no capacity available to redeploy.
                // In that case we should leave the node in the active state to avoid failing additional nodes.
                nodeRepository().reactivate(node.hostname(), Agent.NodeFailer,
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
        Instant startOfThrottleWindow = clock.instant().minus(throttlePolicy.throttleWindow);
        List<Node> nodes = nodeRepository().getNodes();
        NodeList recentlyFailedNodes = nodes.stream()
                                            .filter(n -> n.history().hasEventAfter(History.Event.Type.failed, startOfThrottleWindow))
                                            .collect(collectingAndThen(Collectors.toList(), NodeList::copyOf));

        // Allow failing nodes within policy
        if (recentlyFailedNodes.size() < throttlePolicy.allowedToFailOf(nodes.size())) return false;

        // Always allow failing physical nodes up to minimum limit
        if (!node.parentHostname().isPresent() &&
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
