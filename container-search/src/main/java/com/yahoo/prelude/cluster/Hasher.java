// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.yahoo.container.handler.VipStatus;
import com.yahoo.log.LogLevel;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;

/**
 * Failover between multiple Vespa backends.
 *
 * @author bratseth
 * @author Prashanth B. Bhat
 * @author Steinar Knutsen
 */
public class Hasher {

    boolean running = false;

    private static final Logger log = Logger.getLogger(Hasher.class.getName());
    private static final Random tldSeeder = new Random();

    private volatile VespaBackEndSearcher[] allNodes = new VespaBackEndSearcher[0];
    private volatile VespaBackEndSearcher[] localNodes = new VespaBackEndSearcher[0];

    private AtomicInteger avoidAllQrsHitSameTld = new AtomicInteger(tldSeed());

    /**
     * Creates a hasher independent of the {@linkplain VipStatus programmatic VIP API}.
     */
    public Hasher() {
    }

    private static synchronized int tldSeed() {
        return tldSeeder.nextInt();
    }

    static private VespaBackEndSearcher[] addNode(VespaBackEndSearcher node, VespaBackEndSearcher[] oldNodes) {
        assert node != null;
        for (VespaBackEndSearcher n : oldNodes) {
            if (n == node) return oldNodes; // already present
        }
        VespaBackEndSearcher[] newNodes = new VespaBackEndSearcher[oldNodes.length + 1];
        System.arraycopy(oldNodes, 0, newNodes, 0, oldNodes.length);
        newNodes[oldNodes.length] = node;
        return newNodes;
    }

    /**
     * Make a node available for search.
     */
    public void add(VespaBackEndSearcher node) {
        allNodes = addNode(node, allNodes);

        if (node.isLocalDispatching()) {
            localNodes = addNode(node, localNodes);
        }
    }

    private VespaBackEndSearcher[] removeNode(VespaBackEndSearcher node, VespaBackEndSearcher[] oldNodes) {
        int newLen = oldNodes.length;
        for (VespaBackEndSearcher n : oldNodes) {
            if (n == node) {
                --newLen;
            }
        }
        if (newLen == oldNodes.length) {
            return oldNodes;
        }
        VespaBackEndSearcher[] newNodes = new VespaBackEndSearcher[newLen];
        int idx = 0;
        for (VespaBackEndSearcher n : oldNodes) {
            if (n != node) {
                newNodes[idx++] = n;
            }
        }
        assert idx == newLen;
        return newNodes;
    }

    /** Removes a node */
    public void remove(VespaBackEndSearcher node) {
        if (allNodes.length == 0) return;

        VespaBackEndSearcher[] newNodes = removeNode(node, allNodes);
        if (newNodes != allNodes) {
            if (running && newNodes.length == 0) {
                log.log(LogLevel.WARNING, "No longer any nodes for this cluster when"
                                          + " removing malfunctioning " + node.toString() + ".");
            }
            allNodes = newNodes;
        }

        newNodes = removeNode(node, localNodes);
        if (newNodes != localNodes) {
            if (running && localNodes.length == 0) {
                log.log(LogLevel.WARNING, "Removing malfunctioning " + node.toString()
                                          + " from traffic leaves no local dispatchers, performance"
                                          + " degradation is to expected.");
            }
            localNodes = newNodes;
        }
    }

    public int getNodeCount() {
        return allNodes.length;
    }

    /**
     * Return a node, prefer local nodes, try to skip already hit nodes.
     *
     * @param trynum hint to skip already used nodes
     * @return the selected node, or null if this hasher has no nodes
     */
    public VespaBackEndSearcher select(int trynum) {
        VespaBackEndSearcher[] nodes = allNodes;

        if (localNodes.length > 0) {
            nodes = localNodes;
        }
        if (nodes.length == 0) {
            return null;
        }
        int idx = 0;
        if (nodes.length > 1) {
            idx = Math.abs(avoidAllQrsHitSameTld.incrementAndGet() % nodes.length);
        }
        assert nodes[idx] != null;
        return nodes[idx];
    }

}
