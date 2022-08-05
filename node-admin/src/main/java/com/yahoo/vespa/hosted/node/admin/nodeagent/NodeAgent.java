// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.node.admin.container.ContainerStats;

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
     * Starts the agent. After this method is called, the agent will asynchronously maintain the node, continuously
     * striving to make the current state equal to the wanted state.
     */
    void start(NodeAgentContext context);

    /**
     * Stop the node in anticipation of host suspension, e.g. reboot or docker upgrade.
     */
    void stopForHostSuspension(NodeAgentContext context);

    /**
     * Signals to the agent that the node is at the end of its lifecycle and no longer needs a managing agent.
     * Cleans up any resources the agent owns, such as threads, connections etc. Cleanup is synchronous; when this
     * method returns, no more actions will be taken by the agent.
     */
    void stopForRemoval(NodeAgentContext context);

    /**
     * Updates metric receiver with the latest node-agent stats, and returns the container stats if available.
     */
    default Optional<ContainerStats> updateContainerNodeMetrics(NodeAgentContext context, boolean isSuspended) { return Optional.empty(); }

    /**
     * Returns and resets number of unhandled exceptions
     */
    int getAndResetNumberOfUnhandledExceptions();
}
