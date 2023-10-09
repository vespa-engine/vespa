// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.yolean.UncheckedInterruptedException;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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

    private static final Logger log = Logger.getLogger(ClusterMonitor.class.getName());

    private final MonitorConfiguration configuration = new MonitorConfiguration();

    private final NodeManager<T> nodeManager;

    private final MonitorThread monitorThread;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** A map from Node to corresponding MonitoredNode */
    private final Map<T, TrafficNodeMonitor<T>> nodeMonitors = Collections.synchronizedMap(new LinkedHashMap<>());

    // Used during reconfiguration to ensure async RPC calls are complete.
    private final Set<T> nodesToRemove = new LinkedHashSet<>();

    // Used during reconfiguration to ensure all nodes have data.
    private final Set<T> nodesToUpdate = new LinkedHashSet<>();

    // Used for reconfiguration, and during shutdown.
    private boolean skipNextWait = false;

    public ClusterMonitor(NodeManager<T> manager, boolean startPingThread) {
        nodeManager = manager;
        monitorThread = new MonitorThread("search.clustermonitor." + manager.name());
        if (startPingThread) {
            monitorThread.start();
        }
    }

    /** Updates the monitored set of nodes, and waits for 1. data on new nodes, and 2. RPC completion of removed nodes. */
    public synchronized void reconfigure(Collection<T> nodes) {
        if ( ! monitorThread.isAlive()) throw new IllegalStateException("monitor thread must be alive for reconfiguration");

        nodesToUpdate.addAll(nodes);
        nodesToRemove.addAll(nodeMonitors.keySet());
        nodesToRemove.removeAll(nodes);
        for (T node : nodes) if ( ! nodeMonitors.containsKey(node)) add(node, true);

        synchronized (nodeManager) { skipNextWait = true; nodeManager.notifyAll(); }
        try { while ( ! nodesToRemove.isEmpty() || ! nodesToUpdate.isEmpty()) wait(1); }
        catch (InterruptedException e) { throw new UncheckedInterruptedException(e, true); }

        nodeManager.pingIterationCompleted();
    }

    public void start() {
        if ( ! monitorThread.isAlive()) {
            monitorThread.start();
        }
    }

    /** Returns the configuration of this cluster monitor */
    public MonitorConfiguration getConfiguration() { return configuration; }

    public boolean isClosed() { return closed.get(); }

    /**
     * Adds a new node for monitoring.
     * The object representing the node must
     * <ul>
     * <li>Have a sensible toString</li>
     * <li>Have a sensible identity (equals and hashCode)</li>
     * </ul>
     *
     * @param node the object representing the node
     * @param internal whether this node is internal to this cluster
     */
    public void add(T node, boolean internal) {
        nodeMonitors.put(node, new TrafficNodeMonitor<>(node, configuration, internal));
    }

    /** Called from ClusterSearcher/NodeManager when a node failed */
    public synchronized void failed(T node, ErrorMessage error) {
        updateMonitoredNode(node, monitor -> monitor.failed(error), nodeManager::failed);
    }

    /** Called when a node responded */
    public synchronized void responded(T node) {
        updateMonitoredNode(node, TrafficNodeMonitor::responded, nodeManager::working);
    }

    private void updateMonitoredNode(T node, Consumer<TrafficNodeMonitor<T>> monitorUpdate, Consumer<T> nodeUpdate) {
        TrafficNodeMonitor<T> monitor = nodeMonitors.get(node);

        // Don't touch state during shutdown.
        if (closed.get()) monitor = null;

        // Node was removed during reconfiguration, and should no longer be monitored.
        if (nodesToRemove.remove(node)) {
            nodeMonitors.remove(node);
            monitor = null;
        }

        // Update monitor state only when it actually changes.
        if (monitor != null) {
            Boolean wasWorking = monitor.isKnownWorking();
            monitorUpdate.accept(monitor);
            if (wasWorking != monitor.isKnownWorking())
                nodeUpdate.accept(node);
        }

        // If the node was added in a recent reconfiguration, we now have its required data.
        nodesToUpdate.remove(node);
    }

    /**
     * Ping all nodes which needs pinging to discover state changes
     */
    public synchronized void ping(Executor executor) {
        for (var monitor : nodeMonitors()) {
            if (closed.get()) return; // Do nothing to change state if close has started.
            if (nodesToRemove.remove(monitor.getNode())) {
                nodeMonitors.remove(monitor.getNode());
                continue;
            }
            nodeManager.ping(this, monitor.getNode(), executor);
        }
        nodeManager.pingIterationCompleted();
    }

    /** Returns a thread-safe snapshot of the NodeMonitors of all added nodes */
    public Iterator<BaseNodeMonitor<T>> nodeMonitorIterator() {
        return nodeMonitors().iterator();
    }

    /** Returns a thread-safe snapshot of the NodeMonitors of all added nodes */
    public List<BaseNodeMonitor<T>> nodeMonitors() {
        return List.copyOf(nodeMonitors.values());
    }

    /** Must be called when this goes out of use */
    public void shutdown() {
        closed.set(true);
        synchronized (this) {
            nodeMonitors.clear();
        }
        synchronized (nodeManager) {
            skipNextWait = true;
            nodeManager.notifyAll();
        }
        try {
            if (monitorThread.isAlive()) {
                monitorThread.join();
            }
        } catch (InterruptedException e) {}
    }

    private class MonitorThread extends Thread {
        MonitorThread(String name) {
            super(name);
            setDaemon(true);
        }

        public void run() {
            log.info("Starting cluster monitor thread " + getName());
            // Pings must happen in a separate thread from this to handle timeouts
            // By using a cached thread pool we ensured that 1) a single thread will be used
            // for all pings when there are no problems (important because it ensures that
            // any thread local connections are reused) 2) a new thread will be started to execute
            // new pings when a ping is not responding
            ExecutorService pingExecutor = Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory("search.ping"));
            while (!closed.get()) {
                try {
                    log.finest("Activating ping");
                    ping(pingExecutor);
                    synchronized (nodeManager) {
                        if ( ! skipNextWait)
                            nodeManager.wait(configuration.getCheckInterval());
                        skipNextWait = false;
                    }
                }
                catch (Throwable e) {
                    if (closed.get() && e instanceof InterruptedException) {
                        break;
                    } else if ( ! (e instanceof Exception) ) {
                        log.log(Level.WARNING,"Error in monitor thread, will quit", e);
                        break;
                    } else {
                        log.log(Level.WARNING,"Exception in monitor thread", e);
                    }
                }
            }
            pingExecutor.shutdown();
            try {
                if ( ! pingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warning("Timeout waiting for ping executor to terminate");
                }
            } catch (InterruptedException e) { }
            log.info("Stopped cluster monitor thread " + getName());
        }

    }

}
