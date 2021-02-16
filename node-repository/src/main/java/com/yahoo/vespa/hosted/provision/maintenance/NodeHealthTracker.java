// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;

/**
 * Checks if nodes are responding and updates their status accordingly
 *
 * @author bratseth
 */
public class NodeHealthTracker extends NodeRepositoryMaintainer {

    /** Provides information about the status of ready hosts */
    private final HostLivenessTracker hostLivenessTracker;

    /** Provides (more accurate) information about the status of active hosts */
    private final ServiceMonitor serviceMonitor;

    public NodeHealthTracker(HostLivenessTracker hostLivenessTracker,
                             ServiceMonitor serviceMonitor, NodeRepository nodeRepository,
                             Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.hostLivenessTracker = hostLivenessTracker;
        this.serviceMonitor = serviceMonitor;
    }

    @Override
    protected boolean maintain() {
        updateReadyNodeLivenessEvents();
        updateActiveNodeDownState();
        return true;
    }

    private void updateReadyNodeLivenessEvents() {
        // Update node last request events through ZooKeeper to collect request to all config servers.
        // We do this here ("lazily") to avoid writing to zk for each config request.
        try (Mutex lock = nodeRepository().nodes().lockUnallocated()) {
            for (Node node : nodeRepository().nodes().list(Node.State.ready)) {
                Optional<Instant> lastLocalRequest = hostLivenessTracker.lastRequestFrom(node.hostname());
                if (lastLocalRequest.isEmpty()) continue;

                if (!node.history().hasEventAfter(History.Event.Type.requested, lastLocalRequest.get())) {
                    History updatedHistory = node.history()
                                                 .with(new History.Event(History.Event.Type.requested, Agent.NodeHealthTracker, lastLocalRequest.get()));
                    nodeRepository().nodes().write(node.with(updatedHistory), lock);
                }
            }
        }
    }

    /**
     * If the node is down (see {@link #allDown}), and there is no "down" history record, we add it.
     * Otherwise we remove any "down" history record.
     */
    private void updateActiveNodeDownState() {
        NodeList activeNodes = nodeRepository().nodes().list(Node.State.active);
        serviceMonitor.getServiceModelSnapshot().getServiceInstancesByHostName().forEach((hostname, serviceInstances) -> {
            Optional<Node> node = activeNodes.matching(n -> n.hostname().equals(hostname.toString())).first();
            if (node.isEmpty()) return;

            // Already correct record, nothing to do
            boolean isDown = allDown(serviceInstances);
            if (isDown == node.get().isDown()) return;

            // Lock and update status
            ApplicationId owner = node.get().allocation().get().owner();
            try (var lock = nodeRepository().nodes().lock(owner)) {
                node = getNode(hostname.toString(), owner, lock); // Re-get inside lock
                if (node.isEmpty()) return; // Node disappeared or changed allocation
                if (isDown) {
                    recordAsDown(node.get(), lock);
                } else {
                    clearDownRecord(node.get(), lock);
                }
            } catch (ApplicationLockException e) {
                // Fine, carry on with other nodes. We'll try updating this one in the next run
                log.log(Level.WARNING, "Could not lock " + owner + ": " + Exceptions.toMessageString(e));
            }
        });
    }

    /**
     * Returns true if the node is considered bad: All monitored services services are down.
     * If a node remains bad for a long time, the NodeFailer will try to fail the node.
     */
    static boolean allDown(List<ServiceInstance> services) {
        Map<ServiceStatus, Long> countsByStatus = services.stream()
                                                          .collect(Collectors.groupingBy(ServiceInstance::serviceStatus, counting()));

        return countsByStatus.getOrDefault(ServiceStatus.UP, 0L) <= 0L &&
               countsByStatus.getOrDefault(ServiceStatus.DOWN, 0L) > 0L;
    }

    /** Get node by given hostname and application. The applicationLock must be held when calling this */
    private Optional<Node> getNode(String hostname, ApplicationId application, @SuppressWarnings("unused") Mutex applicationLock) {
        return nodeRepository().nodes().node(hostname, Node.State.active)
                               .filter(node -> node.allocation().isPresent())
                               .filter(node -> node.allocation().get().owner().equals(application));
    }

    /** Record a node as down if not already recorded */
    private void recordAsDown(Node node, Mutex lock) {
        if (node.history().event(History.Event.Type.down).isPresent()) return; // already down: Don't change down timestamp
        nodeRepository().nodes().write(node.downAt(clock().instant(), Agent.NodeHealthTracker), lock);
    }

    /** Clear down record for node, if any */
    private void clearDownRecord(Node node, Mutex lock) {
        if (node.history().event(History.Event.Type.down).isEmpty()) return;
        nodeRepository().nodes().write(node.up(), lock);
    }

}
