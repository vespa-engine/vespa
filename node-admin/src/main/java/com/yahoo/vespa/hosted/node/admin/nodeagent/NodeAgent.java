// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

/**
 * Responsible for management of a single node over its lifecycle.
 * May own its own resources, threads etc. Runs independently, but receives signals
 * on state changes in the environment that may trigger this agent to take actions.
 *
 * @author bakksjo
 */
public interface NodeAgent {
    /**
     * Starts the agent. After this method is called, the agent will asynchronously maintain the node, continuously
     * striving to make the current state equal to the wanted state.
     */
    void start();

    /**
     * Stop the node in anticipation of host suspension, e.g. reboot or docker upgrade.
     */
    void stopForHostSuspension();

    /**
     * Signals to the agent that the node is at the end of its lifecycle and no longer needs a managing agent.
     * Cleans up any resources the agent owns, such as threads, connections etc. Cleanup is synchronous; when this
     * method returns, no more actions will be taken by the agent.
     */
    void stopForRemoval();

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
