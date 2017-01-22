// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Maintains information in the node repo about when this node last responded to ping
 * and fails nodes which have not responded within the given time limit.
 *
 * @author bratseth
 */
public class NodeFailer extends Maintainer {

    private static final Logger log = Logger.getLogger(NodeFailer.class.getName());

    /** Provides information about the status of ready hosts */
    private final HostLivenessTracker hostLivenessTracker;
    
    /** Provides (more accurate) information about the status of active hosts */
    private final ServiceMonitor serviceMonitor;

    private final Deployer deployer;
    private final Duration downTimeLimit;
    private final Clock clock;
    private final Orchestrator orchestrator;
    
    private final Duration nodeRequestInterval = Duration.ofMinutes(10);
    private final Instant constructionTime;

    public NodeFailer(Deployer deployer, HostLivenessTracker hostLivenessTracker, 
                      ServiceMonitor serviceMonitor, NodeRepository nodeRepository,
                      Duration downTimeLimit, Clock clock, Orchestrator orchestrator) {
        // check ping status every five minutes, but at least twice as often as the down time limit
        super(nodeRepository, min(downTimeLimit.dividedBy(2), Duration.ofMinutes(5)));
        this.deployer = deployer;
        this.hostLivenessTracker = hostLivenessTracker;
        this.serviceMonitor = serviceMonitor;
        this.downTimeLimit = downTimeLimit;
        this.clock = clock;
        this.orchestrator = orchestrator;
        constructionTime = clock.instant();
    }

    private static Duration min(Duration d1, Duration d2) {
        return d1.toMillis() < d2.toMillis() ? d1 : d2;
    }

    @Override
    protected void maintain() {
        // Ready nodes
        updateNodeLivenessEventsForReadyNodes();
        for (Node node : readyNodesWhichAreDead( )) {
            // ready Docker hosts and nodes do not run Vespa services, so skip those
            if (node.flavor().getType() == Flavor.Type.DOCKER_CONTAINER || node.type() == NodeType.host)
                continue;
            nodeRepository().fail(node.hostname());
        }
        for (Node node : readyNodesWithHardwareFailure())
            nodeRepository().fail(node.hostname());

        // Active nodes
        for (Node node : determineActiveNodeDownStatus()) {
            Instant graceTimeEnd = node.history().event(History.Event.Type.down).get().at().plus(downTimeLimit);
            if (graceTimeEnd.isBefore(clock.instant()) && ! applicationSuspended(node) && failAllowedFor(node.type()))
                failActive(node);
        }
    }
    
    private void updateNodeLivenessEventsForReadyNodes() {
        // Update node last request events through ZooKeeper to collect request to all config servers.
        // We do this here ("lazily") to avoid writing to zk for each config request.
        try (Mutex lock = nodeRepository().lockUnallocated()) {
            for (Node node : nodeRepository().getNodes(Node.State.ready)) {
                Optional<Instant> lastLocalRequest = hostLivenessTracker.lastRequestFrom(node.hostname());
                if ( ! lastLocalRequest.isPresent()) continue;

                Optional<History.Event> recordedRequest = node.history().event(History.Event.Type.requested);
                if ( ! recordedRequest.isPresent() || recordedRequest.get().at().isBefore(lastLocalRequest.get())) {
                    History updatedHistory = node.history().with(new History.Event(History.Event.Type.requested,
                                                                                   lastLocalRequest.get()));
                    nodeRepository().write(node.with(updatedHistory));
                }
            }
        }
    }

    private List<Node> readyNodesWhichAreDead() {    
        // Allow requests some time to be registered in case all config servers have been down
        if (constructionTime.isAfter(clock.instant().minus(nodeRequestInterval).minus(nodeRequestInterval) ))
            return Collections.emptyList();
        
        // Nodes are taken as dead if they have not made a config request since this instant.
        // Add 10 minutes to the down time limit to allow nodes to make a request that infrequently.
        Instant oldestAcceptableRequestTime = clock.instant().minus(downTimeLimit).minus(nodeRequestInterval);
        
        return nodeRepository().getNodes(Node.State.ready).stream()
                .filter(node -> wasMadeReadyBefore(oldestAcceptableRequestTime, node))
                .filter(node -> ! hasRecordedRequestAfter(oldestAcceptableRequestTime, node))
                .collect(Collectors.toList());
    }

    private boolean wasMadeReadyBefore(Instant instant, Node node) {
        Optional<History.Event> readiedEvent = node.history().event(History.Event.Type.readied);
        if ( ! readiedEvent.isPresent()) return false;
        return readiedEvent.get().at().isBefore(instant);
    }

    private boolean hasRecordedRequestAfter(Instant instant, Node node) {
        Optional<History.Event> lastRequest = node.history().event(History.Event.Type.requested);
        if ( ! lastRequest.isPresent()) return false;
        return lastRequest.get().at().isAfter(instant);
    }

    private List<Node> readyNodesWithHardwareFailure() {
        return nodeRepository().getNodes(Node.State.ready).stream()
                .filter(node -> node.status().hardwareFailure().isPresent())
                .collect(Collectors.toList());
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
     * We can attempt to fail any number of *tenant* nodes because the operation will not be effected unless
     * the node is replaced.
     * However, nodes of other types are not replaced (because all of the type are used by a single application),
     * so we only allow one to be in failed at any point in time to protect against runaway failing.
     */
    private boolean failAllowedFor(NodeType nodeType) {
        if (nodeType == NodeType.tenant) return true;
        return nodeRepository().getNodes(nodeType, Node.State.failed).size() == 0;
    }
    
    /**
     * If the node is positively DOWN, and there is no "down" history record, we add it.
     * If the node is positively UP we remove any "down" history record.
     *
     * @return a list of all nodes which are positively currently in the down state
     */
    private List<Node> determineActiveNodeDownStatus() {
        List<Node> downNodes = new ArrayList<>();
        for (ApplicationInstance<ServiceMonitorStatus> application : serviceMonitor.queryStatusOfAllApplicationInstances().values()) {
            for (ServiceCluster<ServiceMonitorStatus> cluster : application.serviceClusters()) {
                for (ServiceInstance<ServiceMonitorStatus> service : cluster.serviceInstances()) {
                    Optional<Node> node = nodeRepository().getNode(service.hostName().s(), Node.State.active);
                    if ( ! node.isPresent()) continue; // we also get status from infrastructure nodes, which are not in the repo. TODO: remove when proxy nodes are in node repo everywhere

                    if (service.serviceStatus().equals(ServiceMonitorStatus.DOWN))
                        downNodes.add(recordAsDown(node.get()));
                    else if (service.serviceStatus().equals(ServiceMonitorStatus.UP))
                        clearDownRecord(node.get());
                    // else: we don't know current status; don't take any action until we have positive information
                }
            }
        }
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
     * which is when the node repo has available capacity to replace the node.
     * Otherwise not replacing the node ensures (by Orchestrator check) that no further action will be taken.
     */
    private void failActive(Node node) {
        Optional<Deployment> deployment =
            deployer.deployFromLocalActive(node.allocation().get().owner(), Duration.ofMinutes(30));
        if ( ! deployment.isPresent()) return; // this will be done at another config server

        try (Mutex lock = nodeRepository().lock(node.allocation().get().owner())) {
            node = nodeRepository().fail(node.hostname());
            try {
                deployment.get().prepare();
                deployment.get().activate();
            }
            catch (RuntimeException e) {
                // The expected reason for deployment to fail here is that there is no capacity available to redeploy.
                // In that case we should leave the node in the active state to avoid failing additional nodes.
                nodeRepository().reactivate(node.hostname());
                log.log(Level.WARNING, "Attempted to fail " + node + " for " + node.allocation().get().owner() +
                                       ", but redeploying without the node failed", e);
            }
        }
    }

    @Override
    public String toString() { return "Node failer"; }

}
