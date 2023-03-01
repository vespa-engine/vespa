// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.jdisc.Metric;
import com.yahoo.lang.MutableInteger;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
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

    /** Provides (more accurate) information about the status of active hosts */
    private final ServiceMonitor serviceMonitor;

    public NodeHealthTracker(ServiceMonitor serviceMonitor, NodeRepository nodeRepository,
                             Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.serviceMonitor = serviceMonitor;
    }

    @Override
    protected double maintain() {
        return updateNodeHealth();
    }

    /**
     * Update UP and DOWN node records for each node as they change.
     */
    private double updateNodeHealth() {
        var attempts = new MutableInteger(0);
        var failures = new MutableInteger(0);
        NodeList activeNodes = nodeRepository().nodes().list(Node.State.active);
        serviceMonitor.getServiceModelSnapshot().getServiceInstancesByHostName().forEach((hostname, serviceInstances) -> {
            Optional<Node> node = activeNodes.node(hostname.toString());
            if (node.isEmpty()) return;

            // Already correct record, nothing to do
            boolean isDown = allDown(serviceInstances);
            if (isDown == node.get().isDown() && isDown != node.get().isUp()) return;

            // Lock and update status
            ApplicationId owner = node.get().allocation().get().owner();
            try (var lock = nodeRepository().applications().lock(owner)) {
                node = getNode(hostname.toString(), owner, lock); // Re-get inside lock
                if (node.isEmpty()) return; // Node disappeared or changed allocation
                attempts.add(1);
                if (isDown) {
                    recordAsDown(node.get(), lock);
                } else {
                    recordAsUp(node.get(), lock);
                }
            } catch (ApplicationLockException e) {
                // Fine, carry on with other nodes. We'll try updating this one in the next run
                log.log(Level.WARNING, "Could not lock " + owner + ": " + Exceptions.toMessageString(e));
                failures.add(1);
            }
        });
        return asSuccessFactor(attempts.get(), failures.get());
    }

    /**
     * Returns true if the node is considered bad: All monitored services are down.
     * If a node remains bad for a long time, the NodeFailer will try to fail the node.
     */
    static boolean allDown(List<ServiceInstance> services) {
        Map<ServiceStatus, Long> countsByStatus = services.stream()
                                                          .collect(Collectors.groupingBy(ServiceInstance::serviceStatus, counting()));

        return countsByStatus.getOrDefault(ServiceStatus.UP, 0L) <= 0L &&
               countsByStatus.getOrDefault(ServiceStatus.DOWN, 0L) > 0L &&
               countsByStatus.getOrDefault(ServiceStatus.UNKNOWN, 0L) == 0L;
    }

    /** Get node by given hostname and application. The applicationLock must be held when calling this */
    private Optional<Node> getNode(String hostname, ApplicationId application, @SuppressWarnings("unused") Mutex applicationLock) {
        return nodeRepository().nodes().node(hostname)
                               .filter(node -> node.state() == Node.State.active)
                               .filter(node -> node.allocation().isPresent())
                               .filter(node -> node.allocation().get().owner().equals(application));
    }

    /** Record a node as down if not already recorded */
    private void recordAsDown(Node node, Mutex lock) {
        if (node.isDown()) return; // already down: Don't change down timestamp
        nodeRepository().nodes().write(node.downAt(clock().instant(), Agent.NodeHealthTracker), lock);
    }

    /** Clear down record for node, if any */
    private void recordAsUp(Node node, Mutex lock) {
        if (node.isUp()) return; // already up: Don't change up timestamp
        nodeRepository().nodes().write(node.upAt(clock().instant(), Agent.NodeHealthTracker), lock);
    }

}
