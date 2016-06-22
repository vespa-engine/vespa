// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin;

/**
 * Responsible for management of a single node over its lifecycle.
 * May own its own resources, threads etc. Runs independently, but receives signals
 * on state changes in the environment that may trigger this agent to take actions.
 *
 * @author bakksjo
 */
public interface NodeAgent {

    enum Command {UPDATE_FROM_NODE_REPO, FREEZE, UNFREEZE}
    enum State {WAITING, WORKING, DIRTY, FROZEN, TERMINATED}

    /**
     * Signals to the agent that it should update the node specification and container state and maintain wanted state.
     *
     * This method is to be assumed asynchronous by the caller; i.e. any actions the agent will take may execute after
     * this method call returns.
     *
     * It is an error to call this method on an instance after stop() has been called.
     */
    void execute(Command wantedState);

    /**
     * Returns the state of the agent.
     */
    State getState();

    /**
     * Starts the agent. After this method is called, the agent will asynchronously maintain the node, continuously
     * striving to make the current state equal to the wanted state. The current and wanted state update as part of
     * {@link #execute(Command)}.
     */
    void start();

    /**
     * Signals to the agent that the node is at the end of its lifecycle and no longer needs a managing agent.
     * Cleans up any resources the agent owns, such as threads, connections etc. Cleanup is synchronous; when this
     * method returns, no more actions will be taken by the agent.
     */
    void terminate();
}
