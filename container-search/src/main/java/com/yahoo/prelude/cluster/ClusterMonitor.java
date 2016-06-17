// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster;

import java.util.Map;
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
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ClusterMonitor implements Runnable, Freezable {

    private final MonitorConfiguration configuration;

    private final static Logger log = Logger.getLogger(ClusterMonitor.class.getName());

    private final ClusterSearcher nodeManager;

    private final VipStatus vipStatus;

    /** A map from Node to corresponding MonitoredNode */
    private final Map<VespaBackEndSearcher, NodeMonitor> nodeMonitors = new java.util.IdentityHashMap<>();
    ScheduledFuture<?>  future;

    private boolean isFrozen = false;

    ClusterMonitor(final ClusterSearcher manager, final QrMonitorConfig monitorConfig, VipStatus vipStatus) {
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
        if (!isFrozen()) {
            throw new IllegalStateException(
                    "Do not start the monitoring thread before the set of"
                    +" nodes to monitor is complete/the ClusterMonitor is frozen.");
        }
        future = nodeManager.getScheduledExecutor().scheduleAtFixedRate(this, 30 * 1000, configuration.getCheckInterval(), TimeUnit.MILLISECONDS);
    }

    /**
     * Adds a new node for monitoring.
     */
    void add(final VespaBackEndSearcher node) {
        if (isFrozen()) {
            throw new IllegalStateException(
                    "Can not add new nodes after ClusterMonitor has been frozen.");
        }
        final NodeMonitor monitor = new NodeMonitor(node);
        nodeMonitors.put(node, monitor);
    }

    /** Called from ClusterSearcher/NodeManager when a node failed */
    void failed(final VespaBackEndSearcher node, final ErrorMessage error) {
        final NodeMonitor monitor = nodeMonitors.get(node);
        final boolean wasWorking = monitor.isWorking();
        monitor.failed(error);
        if (wasWorking && !monitor.isWorking()) {
            // was warning, see VESPA-1922            
            log.info("Failed monitoring node '" + node + "' due to '" + error);
            nodeManager.failed(node);
        }
        updateVipStatus();
    }

    /** Called when a node responded */
    void responded(final VespaBackEndSearcher node, boolean hasDocumentsOnline) {
        final NodeMonitor monitor = nodeMonitors.get(node);
        final boolean wasFailing = !monitor.isWorking();
        monitor.responded(hasDocumentsOnline);
        if (wasFailing && monitor.isWorking()) {
            log.info("Failed node '" + node + "' started working again.");
            nodeManager.working(monitor.getNode());
        }
        updateVipStatus();
    }

    private void updateVipStatus() {
        boolean hasWorkingNodesWithDocumentsOnline = false;
        for (NodeMonitor node : nodeMonitors.values()) {
            if (node.isWorking() && node.searchNodesOnline()) {
                hasWorkingNodesWithDocumentsOnline = true;
                break;
            }
        }
        if (hasWorkingNodesWithDocumentsOnline) {
            vipStatus.addToRotation(this);
        } else {
            vipStatus.removeFromRotation(this);
        }
    }

    /**
     * Ping all nodes which needs pinging to discover state changes
     */
    private void ping() throws InterruptedException {
        for (final NodeMonitor monitor : nodeMonitors.values()) {
            nodeManager.ping(monitor.getNode());
        }
    }

    @Override
    public void run() {
        log.finest("Activating ping");
        try {
            ping();
        } catch (final Exception e) {
            log.log(Level.WARNING, "Error in monitor thread", e);
        }
    }

    public void shutdown() throws InterruptedException {
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
