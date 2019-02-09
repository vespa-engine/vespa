// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;

import java.time.Duration;
import java.util.List;

/**
 * NodeAdmin manages the life cycle of NodeAgents.
 * @author dybis
 */
public interface NodeAdmin {

    /**
     * Calling this will cause NodeAdmin to move to the state containersToRun by adding or removing nodes.
     * @param containersToRun this is the wanted state.
     */
    void refreshContainersToRun(final List<NodeSpec> containersToRun);

    /** Gather node agent and its docker container metrics and forward them to the {@code MetricReceiverWrapper} */
    void updateNodeAgentMetrics();

    /**
     * Attempts to freeze/unfreeze all NodeAgents and itself. To freeze a NodeAgent means that
     * they will not pick up any changes from NodeRepository.
     *
     * @param frozen whether NodeAgents and NodeAdmin should be frozen
     * @return True if all the NodeAgents and NodeAdmin has converged to the desired state
     */
    boolean setFrozen(boolean frozen);

    /**
     * Returns whether the NodeAdmin itself is currently frozen, meaning it will not pick up any changes
     * from NodeRepository.
     */
    boolean isFrozen();

    /**
     * Returns an upper bound on the time some or all parts of the node admin (including agents)
     * have been frozen.  Returns 0 if not frozen, nor trying to freeze.
     */
    Duration subsystemFreezeDuration();

    /**
     * Stop services on these nodes
     * @param nodes List of hostnames to suspend
     */
    void stopNodeAgentServices(List<String> nodes);

    /**
     * Start node-admin schedulers.
     */
    void start();

    /**
     * Stop the NodeAgent. Will not delete the storage or stop the container.
     */
    void stop();
}
