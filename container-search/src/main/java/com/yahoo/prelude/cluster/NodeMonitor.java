// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster;

import static com.yahoo.container.protect.Error.BACKEND_COMMUNICATION_ERROR;
import static com.yahoo.container.protect.Error.NO_ANSWER_WHEN_PINGING_NODE;

import java.util.logging.Logger;

import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.result.ErrorMessage;

/**
 * A node monitor is responsible for maintaining the state of a monitored node.
 * It has the following properties:
 * <ul>
 * <li>A node is taken out of operation if it gives no response in 10 s</li>
 * <li>A node is put back in operation when it responds correctly again</li>
 * <li>A node is initially considered not in operation until we have some data from it</li>
 * </ul>
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class NodeMonitor {

    protected static Logger log = Logger.getLogger(NodeMonitor.class.getName());

    /** The object representing the monitored node */
    private final VespaBackEndSearcher node;

    private boolean isWorking = true;

    /** The last time this node responded successfully */
    private long succeededAt = 0;

    /** Whether it is assumed the node has documents available to serve */
    private boolean searchNodesOnline = false;

    /**
     * Creates a new node monitor for a node
     */
    public NodeMonitor(final VespaBackEndSearcher node) {
        this.node = node;
    }

    /**
     * Returns whether this node is currently in a state suitable for receiving
     * traffic. As far as we know, that is
     */
    public boolean isWorking() {
        return isWorking;
    }

    // Whether or not dispatch has ever responded successfully
    private boolean statusIsKnown = false;

    public VespaBackEndSearcher getNode() {
        return node;
    }

    /**
     * Called when this node fails.
     *
     * @param error a container which should contain a short description
     */
    public void failed(ErrorMessage error) {
        long respondedAt = System.currentTimeMillis();

        if (error.getCode() == NO_ANSWER_WHEN_PINGING_NODE.code) {
            // Only count not being able to talk to backend at all
            // as errors we care about
            if ((respondedAt - succeededAt) > 10000) {
                this.searchNodesOnline = false;
                setWorking(false, "Not working for 10 s: " + error.toString());
            }
        } else if (error.getCode() == BACKEND_COMMUNICATION_ERROR.code) {
            this.searchNodesOnline = false;
            setWorking(false, "Backend communication error: " + error.toString());
        } else {
            succeededAt = respondedAt;
        }
    }

    /**
     * Called when a response is received from this node.
     */
    public void responded(boolean searchNodesOnline) {
        succeededAt = System.currentTimeMillis();
        this.searchNodesOnline = searchNodesOnline;
        if (! isWorking)
            setWorking(true, "Responds correctly");
        statusIsKnown = true;
    }

    /** Changes the state of this node if required */
    private void setWorking(boolean working, String explanation) {
        if (isWorking == working) return; // Old news

        if (statusIsKnown) {
            if (working)
                log.info("Putting " + node + " in service: " + explanation);
            else
                log.info("Taking " + node + " out of service: " + explanation);
        }

        isWorking = working;
    }

    boolean searchNodesOnline() { return searchNodesOnline; }

    /** Returns true if we have had enough time to determine the status of this node since creating the monitor */
    boolean statusIsKnown() { return statusIsKnown; }

}
