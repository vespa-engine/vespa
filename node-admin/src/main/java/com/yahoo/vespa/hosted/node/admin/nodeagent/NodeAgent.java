// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;

import java.util.Map;
import java.util.Optional;

/**
 * Responsible for management of a single node over its lifecycle.
 * May own its own resources, threads etc. Runs independently, but receives signals
 * on state changes in the environment that may trigger this agent to take actions.
 *
 * @author bakksjo
 */
public interface NodeAgent {
    /**
     * Freeze will eventually cause the NodeAgent to not pick up changes. Check isFrozen to see state.
     */
    void freeze();

    /**
     * start picking up changes again.
     */
    void unfreeze();

    void stopServices(ContainerName containerName);

    /**
     * Make NodeAgent check for work to be done.
     */
    void signalWorkToBeDone();


    /**
     * Returns true if NodeAgent is frozen.
     */
    boolean isFrozen();

    /**
     * Returns a map containing all relevant NodeAgent variables and their current values.
     */
    Map<String, Object> debugInfo();

    /**
     * Starts the agent. After this method is called, the agent will asynchronously maintain the node, continuously
     * striving to make the current state equal to the wanted state.
     */
    void start(int intervalMillis);

    /**
     * Signals to the agent that the node is at the end of its lifecycle and no longer needs a managing agent.
     * Cleans up any resources the agent owns, such as threads, connections etc. Cleanup is synchronous; when this
     * method returns, no more actions will be taken by the agent.
     */
    void stop();

    /**
     * Returns the {@link ContainerNodeSpec} for this node agent.
     */
    Optional<ContainerNodeSpec> getContainerNodeSpec();

    /**
     * Updates metric receiver with the latest node-agent stats
     */
    void updateContainerNodeMetrics();

    /**
     * Returns true if NodeAgent is waiting for an image download to finish
     */
    boolean isDownloadingImage();

    /**
     * Returns and resets number of unhandled exceptions
     */
    int getAndResetNumberOfUnhandledExceptions();
}
