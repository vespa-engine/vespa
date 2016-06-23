// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maintains information in the node repo about when this node last responded to ping
 * and fails nodes which have not responded within the given time limit.
 *
 * @author bratseth
 */
public class NodeFailer extends Maintainer {

    private static final Logger log = Logger.getLogger(NodeFailer.class.getName());

    private final Deployer deployer;
    private final ServiceMonitor serviceMonitor;
    private final Duration downTimeLimit;
    private final Clock clock;
    private final Orchestrator orchestrator;

    public NodeFailer(Deployer deployer, ServiceMonitor serviceMonitor, NodeRepository nodeRepository,
                      Duration downTimeLimit, Clock clock, Orchestrator orchestrator) {
        // check ping status every five minutes, but at least twice as often as the down time limit
        super(nodeRepository, min(downTimeLimit.dividedBy(2), Duration.ofMinutes(5)));
        this.deployer = deployer;
        this.serviceMonitor = serviceMonitor;
        this.downTimeLimit = downTimeLimit;
        this.clock = clock;
        this.orchestrator = orchestrator;
    }

    private static Duration min(Duration d1, Duration d2) {
        return d1.toMillis() < d2.toMillis() ? d1 : d2;
    }

    @Override
    protected void maintain() {
        List<Node> downNodes = maintainDownStatus();

        for (Node node : downNodes) {
            // Grace time before failing the node
            Instant graceTimeEnd = node.history().event(History.Event.Type.down).get().at().plus(downTimeLimit);

            if (graceTimeEnd.isBefore(clock.instant()) && ! applicationSuspended(node))
                fail(node);
        }
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
     * If the node is positively DOWN, and there is no "down" history record, we add it.
     * If the node is positively UP we remove any "down" history record.
     *
     * @return a list of all nodes which are positively currently in the down state
     */
    private List<Node> maintainDownStatus() {
        List<Node> downNodes = new ArrayList<>();
        for (ApplicationInstance<ServiceMonitorStatus> application : serviceMonitor.queryStatusOfAllApplicationInstances().values()) {
            for (ServiceCluster<ServiceMonitorStatus> cluster : application.serviceClusters()) {
                for (ServiceInstance<ServiceMonitorStatus> service : cluster.serviceInstances()) {
                    Optional<Node> node = nodeRepository().getNode(Node.State.active, service.hostName().s());
                    if ( ! node.isPresent()) continue; // we also get status from infrastructure nodes, which are not in the repo

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
            node = nodeRepository().getNode(Node.State.active, node.hostname()).get(); // re-get inside lock
            return nodeRepository().write(node.setDown(clock.instant()));
        }
    }

    private void clearDownRecord(Node node) {
        if ( ! node.history().event(History.Event.Type.down).isPresent()) return;

        try (Mutex lock = nodeRepository().lock(node.allocation().get().owner())) {
            node = nodeRepository().getNode(Node.State.active, node.hostname()).get(); // re-get inside lock
            nodeRepository().write(node.setUp());
        }
    }

    /**
     * Called when a node should be moved to the failed state: Do that if it seems safe,
     * which is when the node repo has available capacity to replace the node.
     * Otherwise not replacing the node ensures (by Orchestrator check) that no further action will be taken.
     */
    private void fail(Node node) {
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
                nodeRepository().unfail(node.hostname());
                log.log(Level.WARNING, "Attempted to fail " + node + " for " + node.allocation().get().owner() +
                                       ", but redeploying without the node failed", e);
            }
        }
    }

    @Override
    public String toString() { return "Node failer"; }

}
