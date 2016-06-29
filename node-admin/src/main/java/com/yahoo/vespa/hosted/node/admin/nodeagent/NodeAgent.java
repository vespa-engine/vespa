// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

/**
 * Responsible for management of a single node over its lifecycle.
 * May own its own resources, threads etc. Runs independently, but receives signals
 * on state changes in the environment that may trigger this agent to take actions.
 *
 * @author bakksjo
 */
public interface NodeAgent {

    enum Command {UPDATE_FROM_NODE_REPO, SET_FREEZE, UNFREEZE}
    enum State {WAITING, WORKING, FROZEN, TERMINATED}

    /**
     * Make the node agent execute a command soon.
     * @param command task to be done
     */
    void execute(Command command);

    /**
     * Returns the state of the agent.
     */
    State getState();

    /**
     * Starts the agent. After this method is called, the agent will asynchronously maintain the node, continuously
     * striving to make the current state equal to the wanted state. The current and wanted state update as part of
     * {@link #execute(Command)}.
     */
    void start(int intervalMillis);

    /**
     * Signals to the agent that the node is at the end of its lifecycle and no longer needs a managing agent.
     * Cleans up any resources the agent owns, such as threads, connections etc. Cleanup is synchronous; when this
     * method returns, no more actions will be taken by the agent.
     */
    void stop();
}
