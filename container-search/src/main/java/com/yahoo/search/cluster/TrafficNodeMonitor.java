// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

import com.yahoo.search.result.ErrorMessage;

import java.util.concurrent.TimeUnit;

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

    @Override
    public T getNode() { return node; }

    /**
     * Called when this node fails.
     *
     * @param error a container which should contain a short description
     */
    @Override
    public synchronized void failed(ErrorMessage error) {
        respondedAt = now();

        switch (error.getCode()) {
            // TODO: Remove hard coded error messages.
            // Refer to docs/errormessages
            case 10:
            case 11:
                // Only count not being able to talk to backend at all
                // as errors we care about
                long failedTime = (respondedAt-succeededAt);
                if (failedTime > configuration.getFailLimit()) {
                    setWorking(false, "Not working for " + failedTime + " ms: " + error.toString());
                }
                break;
            default:
                succeededAt = respondedAt;
                break;
        }
    }

    /**
     * Called when a response is received from this node.
     */
    @Override
    public synchronized void responded() {
        respondedAt=now();
        succeededAt=respondedAt;
        atStartUp = false;

        if (!isWorking)
            setWorking(true,"Responds correctly");
        notifyAll();
    }

    /** Thread-safely changes the state of this node if required */
    @Override
    protected synchronized void setWorking(boolean working,String explanation) {
        if (this.isWorking==working) return; // Old news

        if (explanation==null) {
            explanation="";
        } else {
            explanation=": " + explanation;
        }

        if (working) {
            log.info("Putting " + node + " in service" + explanation);
        }
        else {
            if (!atStartUp || !isInternal())
                log.warning("Taking " + node + " out of service" + explanation);
            failedAt=now();
        }

        this.isWorking=working;
    }

    @Override
    public synchronized boolean waitUntilStateIsDetermined(long timeout, TimeUnit timeUnit) {
        long doom = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        try {
            for (long remain = doom - System.currentTimeMillis(); atStartUp && remain > 0; remain = doom - System.currentTimeMillis()) {
                wait(remain);
            }
        } catch (InterruptedException e) {}
        return isWorking;
    }

}
