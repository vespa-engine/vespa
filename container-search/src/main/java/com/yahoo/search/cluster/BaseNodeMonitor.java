// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

import java.util.logging.Logger;

import com.yahoo.search.result.ErrorMessage;


/**
 * A node monitor is responsible for maintaining the state of a monitored node.
 * It has the following properties:
 * <ul>
 * <li>A node is taken out of operation if it fails</li>
 * <li>A node is put back in operation when it responds correctly again
 *     <i>responseAfterFailLimit</i> times <b>unless</b>
 *     it has failed <i>failQuarantineLimit</i>. In the latter case it won't
 *     be put into operation again before that time period has expired</li>
 * </ul>
 *
 * @author bratseth
 */
public abstract class BaseNodeMonitor<T> {

    protected static Logger log = Logger.getLogger(BaseNodeMonitor.class.getName());

    /** The object representing the monitored node */
    protected T node;

    protected boolean isWorking = true;

    /** Whether this node is quarantined for unstability */
    protected boolean isQuarantined = false;

    /** The last time this node failed, in ms */
    protected long failedAt = 0;

    /** The last time this node responded (failed or succeeded), in ms */
    protected long respondedAt = 0;

    /** The last time this node responded successfully */
    protected long succeededAt = 0;

    /** The configuration of this monitor */
    protected MonitorConfiguration configuration;

    /** Is the node we monitor part of an internal Vespa cluster or not */
    private final boolean internal;

    public BaseNodeMonitor(boolean internal) {
        this.internal=internal;
    }

    public T getNode() { return node; }

    /**
     * Returns whether this node is currently in a state suitable
     * for receiving traffic (default true)
     */
    public boolean isWorking() { return isWorking; }

    /**
     * Called when this node fails.
     *
     * @param error a description of the error
     */
    public abstract void failed(ErrorMessage error);

    /**
     * Called when a response is received from this node. If the node was
     * quarantined and it has been in that state for more than QuarantineTime
     * milliseconds, it is taken out of quarantine.
     *
     * if it is not in quarantine but is not working, it may be set to working
     * if this method is called at least responseAfterFailLimit times
     */
    public abstract void responded();

    protected long now() {
        return System.currentTimeMillis();
    }

    /** Thread-safely changes the state of this node if required */
    protected abstract void setWorking(boolean working,String explanation);

    /** Returns whether or not this is monitoring an internal node. Default is false. */
    public boolean isInternal() { return internal; }

}
