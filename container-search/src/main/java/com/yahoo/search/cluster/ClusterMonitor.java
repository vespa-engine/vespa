// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.search.result.ErrorMessage;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors of a cluster of remote nodes.
 * The monitor uses an internal thread for node monitoring.
 * All <i>public</i> methods of this class are multithread safe.
 *
 * @author bratseth
 */
public class ClusterMonitor<T> {

    private MonitorConfiguration configuration = new MonitorConfiguration();

    private static Logger log=Logger.getLogger(ClusterMonitor.class.getName());

    private NodeManager<T> nodeManager;

    private MonitorThread monitorThread;

    private volatile boolean shutdown = false;

    /** A map from Node to corresponding MonitoredNode */
    private final Map<T, BaseNodeMonitor<T>> nodeMonitors = Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    public ClusterMonitor(NodeManager<T> manager) {
        nodeManager = manager;
        monitorThread = new MonitorThread("search.clustermonitor");
        monitorThread.start();
    }

    /** Returns the configuration of this cluster monitor */
    public MonitorConfiguration getConfiguration() { return configuration; }

    /**
     * Adds a new node for monitoring.
     * The object representing the node must
     * <ul>
     * <li>Have a sensible toString</li>
     * <li>Have a sensible identity (equals and hashCode)</li>
     * </ul>
     *
     * @param node the object representing the node
     * @param internal whether or not this node is internal to this cluster
     */
    public void add(T node, boolean internal) {
        BaseNodeMonitor<T> monitor = new TrafficNodeMonitor<>(node, configuration, internal);
        nodeMonitors.put(node, monitor);
    }

    /**
     * Returns the monitor of the given node, or null if this node has not been added
     */
    public BaseNodeMonitor<T> getNodeMonitor(T node) {
        return nodeMonitors.get(node);
    }

    /** Called from ClusterSearcher/NodeManager when a node failed */
    public synchronized void failed(T node, ErrorMessage error) {
        BaseNodeMonitor<T> monitor = nodeMonitors.get(node);
        boolean wasWorking = monitor.isWorking();
        monitor.failed(error);
        if (wasWorking && !monitor.isWorking()) {
            nodeManager.failed(node);
        }
    }

    /** Called when a node responded */
    public synchronized void responded(T node) {
        BaseNodeMonitor<T> monitor = nodeMonitors.get(node);
        boolean wasFailing =! monitor.isWorking();
        monitor.responded();
        if (wasFailing && monitor.isWorking()) {
            nodeManager.working(monitor.getNode());
        }
    }

    /**
     * Ping all nodes which needs pinging to discover state changes
     */
    public void ping(Executor executor) {
        for (Iterator<BaseNodeMonitor<T>> i = nodeMonitorIterator(); i.hasNext(); ) {
            BaseNodeMonitor<T> monitor= i.next();
            nodeManager.ping(monitor.getNode(), executor); // Cause call to failed or responded
        }
        nodeManager.pingIterationCompleted();
    }

    /** Returns a thread-safe snapshot of the NodeMonitors of all added nodes */
    public Iterator<BaseNodeMonitor<T>> nodeMonitorIterator() {
        return nodeMonitors().iterator();
    }

    /** Returns a thread-safe snapshot of the NodeMonitors of all added nodes */
    public List<BaseNodeMonitor<T>> nodeMonitors() {
        synchronized (nodeMonitors) {
            return new java.util.ArrayList<>(nodeMonitors.values());
        }
    }

    /** Must be called when this goes out of use */
    public void shutdown() {
        shutdown = true;
        monitorThread.interrupt();
    }

    private class MonitorThread extends Thread {
        MonitorThread(String name) {
            super(name);
        }

        public void run() {
            log.fine("Starting cluster monitor thread");
            // Pings must happen in a separate thread from this to handle timeouts
            // By using a cached thread pool we ensured that 1) a single thread will be used
            // for all pings when there are no problems (important because it ensures that
            // any thread local connections are reused) 2) a new thread will be started to execute
            // new pings when a ping is not responding
            Executor pingExecutor=Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory("search.ping"));
            while (!isInterrupted()) {
                try {
                    Thread.sleep(configuration.getCheckInterval());
                    log.finest("Activating ping");
                    ping(pingExecutor);
                }
                catch (Exception e) {
                    if (shutdown && e instanceof InterruptedException) {
                        break;
                    } else {
                        log.log(Level.WARNING,"Error in monitor thread",e);
                    }
                }
            }
            log.fine("Stopped cluster monitor thread");
        }

    }

}
