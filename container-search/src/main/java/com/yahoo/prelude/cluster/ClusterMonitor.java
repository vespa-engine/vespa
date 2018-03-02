// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.yahoo.component.provider.Freezable;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.result.ErrorMessage;

/**
 * Monitors of a cluster of remote nodes. The monitor uses an internal thread
 * for node monitoring.
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class ClusterMonitor implements Runnable, Freezable {

    // The ping thread wil start using the system, but we cannot be guaranteed that all components
    // in the system is up. As a workaround for not being able to find out when the system
    // is ready to be used, we wait some time before starting the ping thread
    private static final int pingThreadInitialDelayMs = 3000;

    private final MonitorConfiguration configuration;

    private final static Logger log = Logger.getLogger(ClusterMonitor.class.getName());

    private final ClusterSearcher nodeManager;

    private final Optional<VipStatus> vipStatus;

    /** A map from Node to corresponding MonitoredNode */
    private final Map<VespaBackEndSearcher, NodeMonitor> nodeMonitors = new java.util.IdentityHashMap<>();

    private ScheduledFuture<?>  future;

    private boolean isFrozen = false;

    ClusterMonitor(ClusterSearcher manager, QrMonitorConfig monitorConfig, Optional<VipStatus> vipStatus) {
        configuration = new MonitorConfiguration(monitorConfig);
        nodeManager = manager;
        this.vipStatus = vipStatus;
        log.fine("checkInterval is " + configuration.getCheckInterval() + " ms");
    }

    /** Returns the configuration of this cluster monitor */
    MonitorConfiguration getConfiguration() {
        return configuration;
    }

    void startPingThread() {
        if ( ! isFrozen())
            throw new IllegalStateException("Do not start the monitoring thread before the set of " +
                                            "nodes to monitor is complete/the ClusterMonitor is frozen.");
        future = nodeManager.getScheduledExecutor().scheduleAtFixedRate(this, pingThreadInitialDelayMs, configuration.getCheckInterval(), TimeUnit.MILLISECONDS);
    }

    /**
     * Adds a new node for monitoring.
     */
    void add(VespaBackEndSearcher node) {
        if (isFrozen())
            throw new IllegalStateException("Can not add new nodes after ClusterMonitor has been frozen.");
        nodeMonitors.put(node, new NodeMonitor(node));
        updateVipStatus();
    }

    /** Called from ClusterSearcher/NodeManager when a node failed */
    void failed(VespaBackEndSearcher node, ErrorMessage error) {
        NodeMonitor monitor = nodeMonitors.get(node);
        boolean wasWorking = monitor.isWorking();
        monitor.failed(error);
        if (wasWorking && !monitor.isWorking()) {
            log.info("Failed monitoring node '" + node + "' due to '" + error);
            nodeManager.failed(node);
        }
        updateVipStatus();
    }

    /** Called when a node responded */
    void responded(VespaBackEndSearcher node, boolean hasSearchNodesOnline) {
        NodeMonitor monitor = nodeMonitors.get(node);
        boolean wasFailing = !monitor.isWorking();
        monitor.responded(hasSearchNodesOnline);
        if (wasFailing && monitor.isWorking()) {
            log.info("Failed node '" + node + "' started working again.");
            nodeManager.working(node);
        }
        updateVipStatus();
    }

    private void updateVipStatus() {
        if ( ! vipStatus.isPresent()) return;
        if ( ! hasInformationAboutAllNodes()) return;
        
        if (hasWorkingNodesWithDocumentsOnline()) {
            vipStatus.get().addToRotation(this);
        } else {
            vipStatus.get().removeFromRotation(this);
        }
    }

    private boolean hasInformationAboutAllNodes() {
        for (NodeMonitor monitor : nodeMonitors.values()) {
            if ( ! monitor.statusIsKnown())
                return false;
        }
        return true;
    }

    private boolean hasWorkingNodesWithDocumentsOnline() {
        for (NodeMonitor node : nodeMonitors.values()) {
            if (node.isWorking() && node.searchNodesOnline())
                return true;
        }
        return false;
    }

    /**
     * Ping all nodes which needs pinging to discover state changes
     */
    private void ping() throws InterruptedException {
        for (NodeMonitor monitor : nodeMonitors.values()) {
            nodeManager.ping(monitor.getNode());
        }
    }

    @Override
    public void run() {
        log.finest("Activating ping");
        try {
            ping();
        } catch (Exception e) {
            log.log(Level.WARNING, "Error in monitor thread", e);
        }
    }

    public void shutdown() {
        if (future != null) {
            future.cancel(true);
        }
    }

    @Override
    public void freeze() {
        isFrozen  = true;

    }

    @Override
    public boolean isFrozen() {
        return isFrozen;
    }

}
