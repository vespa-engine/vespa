// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.search.result.ErrorMessage;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Map<T, TrafficNodeMonitor<T>> nodeMonitors = Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    public ClusterMonitor(NodeManager<T> manager, boolean startPingThread) {
        nodeManager = manager;
        monitorThread = new MonitorThread("search.clustermonitor." + manager.name());
        if (startPingThread) {
            monitorThread.start();
        }
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
     * @param internal whether or not this node is internal to this cluster
     */
    public void add(T node, boolean internal) {
        nodeMonitors.put(node, new TrafficNodeMonitor<>(node, configuration, internal));
    }

    /** Called from ClusterSearcher/NodeManager when a node failed */
    public synchronized void failed(T node, ErrorMessage error) {
        if (closed.get()) return; // Do not touch state if close has started.
        TrafficNodeMonitor<T> monitor = nodeMonitors.get(node);
        Boolean wasWorking = monitor.isKnownWorking();
        monitor.failed(error);
        if (wasWorking != monitor.isKnownWorking())
            nodeManager.failed(node);
    }

    /** Called when a node responded */
    public synchronized void responded(T node) {
        if (closed.get()) return; // Do not touch state if close has started.
        TrafficNodeMonitor<T> monitor = nodeMonitors.get(node);
        Boolean wasWorking = monitor.isKnownWorking();
        monitor.responded();
        if (wasWorking != monitor.isKnownWorking())
            nodeManager.working(node);
    }

    /**
     * Ping all nodes which needs pinging to discover state changes
     */
    public void ping(Executor executor) {
        for (Iterator<BaseNodeMonitor<T>> i = nodeMonitorIterator(); i.hasNext() && !closed.get(); ) {
            BaseNodeMonitor<T> monitor= i.next();
            nodeManager.ping(this, monitor.getNode(), executor); // Cause call to failed or responded
        }
        if (closed.get()) return; // Do nothing to change state if close has started.
        nodeManager.pingIterationCompleted();
    }

    /** Returns a thread-safe snapshot of the NodeMonitors of all added nodes */
    public Iterator<BaseNodeMonitor<T>> nodeMonitorIterator() {
        return nodeMonitors().iterator();
    }

    /** Returns a thread-safe snapshot of the NodeMonitors of all added nodes */
    public List<BaseNodeMonitor<T>> nodeMonitors() {
        return new java.util.ArrayList<>(nodeMonitors.values());
    }

    /** Must be called when this goes out of use */
    public void shutdown() {
        closed.set(true);
        synchronized (this) {
            nodeMonitors.clear();
        }
        synchronized (nodeManager) {
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
            ExecutorService pingExecutor=Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory("search.ping"));
            while (!closed.get()) {
                try {
                    log.finest("Activating ping");
                    ping(pingExecutor);
                    synchronized (nodeManager) {
                        nodeManager.wait(configuration.getCheckInterval());
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
                pingExecutor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) { }
            log.info("Stopped cluster monitor thread " + getName());
        }

    }

}
