// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

import com.yahoo.container.protect.Error;
import com.yahoo.search.result.ErrorMessage;

/**
 * This node monitor is responsible for maintaining the state of a monitored node.
 * It has the following properties:
 * <ul>
 * <li>A node is taken out of operation if it gives no response in 10 s</li>
 * <li>A node is put back in operation when it responds correctly again
 * </ul>
 *
 * @author Steinar Knutsen
 */
public class TrafficNodeMonitor<T> extends BaseNodeMonitor<T> {

    /** Creates a new node monitor for a node */
    public TrafficNodeMonitor(T node, MonitorConfiguration configuration, boolean internal) {
        super(internal);
        this.node = node;
        this.configuration = configuration;
    }

    /** Whether or not this has ever responded successfully */
    private boolean atStartUp = true;

    public T getNode() { return node; }

    /**
     * Called when this node fails.
     *
     * @param error a container which should contain a short description
     */
    @Override
    public void failed(ErrorMessage error) {
        respondedAt = now();

        if (error.getCode() == Error.BACKEND_COMMUNICATION_ERROR.code) {
            setWorking(false, "Connection failure: " + error);
        } else if (error.getCode() == Error.NO_ANSWER_WHEN_PINGING_NODE.code) {
            if ((respondedAt - succeededAt) > 10000) {
                setWorking(false, "Not working for 10 s: " + error);
            }
        } else {
            succeededAt = respondedAt;
        }
    }

    /**
     * Called when a response is received from this node.
     */
    public void responded() {
        respondedAt = now();
        succeededAt = respondedAt;

        setWorking(true,"Responds correctly");
    }

    /**
     * Returns whether this node is currently is a state suitable for receiving traffic, or null if not known
     */
    public Boolean isKnownWorking() { return atStartUp ? null : isWorking; }

    /** Thread-safely changes the state of this node if required */
    @Override
    protected synchronized void setWorking(boolean working, String explanation) {
        atStartUp = false;
        if (this.isWorking == working) return; // Old news

        if (explanation == null) {
            explanation = "";
        } else {
            explanation = ": " + explanation;
        }

        if (working) {
            log.info("Putting " + node + " in service" + explanation);
        } else {
            log.warning("Taking " + node + " out of service" + explanation);
            failedAt = now();
        }

        this.isWorking = working;
    }

}
