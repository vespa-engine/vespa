// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;

import java.time.Duration;
import java.util.Set;

/**
 * NodeAdmin manages the life cycle of NodeAgents.
 * @author Haakon Dybdahl
 */
public interface NodeAdmin {

    /** Start/stop NodeAgents and schedule next NodeAgent ticks with the given NodeAgentContexts */
    void refreshContainersToRun(Set<NodeAgentContext> nodeAgentContexts);

    /** Update node admin metrics */
    void updateMetrics(boolean isSuspended);

    /**
     * Attempts to freeze/unfreeze all NodeAgents and itself. To freeze a NodeAgent means that
     * they will not pick up any changes from NodeRepository.
     *
     * @param frozen whether NodeAgents and NodeAdmin should be frozen
     * @return True if all the NodeAgents and NodeAdmin has converged to the desired state
     */
    boolean setFrozen(boolean frozen);

    /**
     * Returns whether NodeAdmin itself is currently frozen, meaning it will not pick up any changes
     * from NodeRepository.
     */
    boolean isFrozen();

    /**
     * Returns an upper bound on the time some or all parts of the node admin (including agents)
     * have been frozen.  Returns 0 if not frozen, nor trying to freeze.
     */
    Duration subsystemFreezeDuration();

    /**
     * Stop all services on these nodes
     */
    void stopNodeAgentServices();

    /**
     * Start node-admin schedulers.
     */
    void start();

    /**
     * Stop the NodeAgents. Will not delete the storage or stop the container.
     */
    void stop();
}
