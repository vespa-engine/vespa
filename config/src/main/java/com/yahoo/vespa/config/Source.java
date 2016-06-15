// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigurationRuntimeException;

import java.util.logging.Logger;

/**
 * A general config source that retrieves config for its SourceConfig.
 *
 * This class and its subclasses are thread safe.
 *
 * Note that it is the responsibility of the user to set a source's state to OPEN and CANCELLED, and
 * that the READY state can be set by the user as a mark of progress e.g. when waiting for a monitor/lock.
 * All other states are set by this class or one of its subclasses.
 *
 * Originally designed for re-use by closing and reopening, but this caused problems related to
 * synchronization between this class and ConfigInstance.subscribeLock. Currently (2008-05-08)
 * a source cannot be reopened once it has been cancelled.
 *
 * @author <a href="gv@yahoo-inc.com">G. Voldengen</a>
 */
public abstract class Source {

    public enum State { NEW, OPEN_PENDING, READY, OPEN, CANCEL_REQUESTED, CANCELLED }

    protected volatile SourceConfig config;
    protected volatile State state = State.NEW;
    protected long openTimestamp = 0;
    public final static Logger logger = Logger.getLogger(Source.class.getPackage().getName());

    public Source(SourceConfig sourceConfig) {
        this.config = sourceConfig;
    }

    /**
     * Opens this config source.
     * Typically called when the first subscriber subscribes to our ConfigInstance.
     */
    public final synchronized void open() {
        if ((state == State.OPEN) || (state == State.OPEN_PENDING)) {
            return;
        } else if ((state == State.CANCELLED) || (state == State.CANCEL_REQUESTED)) {
            throw new ConfigurationRuntimeException("Subscription with config ID: " + config.getConfigId() + ": Trying to reopen a cancelled source, should not happen.", null);
        }
        state = State.OPEN_PENDING;
        openTimestamp = System.currentTimeMillis();
        myOpen();
        getConfig();
    }

    /**
     * Optional subclass hook for the open() method.
     */
    protected void myOpen() { }

    /**
     * Gets config from this config source.
     */
    public final synchronized void getConfig() {
        if ((state == State.CANCELLED) || (state == State.CANCEL_REQUESTED)) {
            logger.info("Trying to retrieve config from source " + this + " in state: " + state);
            return;
        }
        myGetConfig();
    }

    /**
     * Mandatory subclass hook for the getConfig() method.
     */
    protected abstract void myGetConfig();

    /**
     * Cancels this config source. Typically called when our ConfigInstance has no more subscribers.
     *
     * Irreversible. Reopening a cancelled source would cause problems with multiple threads accessing the source
     * simultaneously. With better synchronization mechanisms it _should_ be possible to close and reopen a source.
     */
    public final void cancel() {
        logger.fine("Closing source " + this + " from state " + state);
        if ((state == State.CANCELLED) || (state == State.CANCEL_REQUESTED)) {
            return;
        }
        state = State.CANCEL_REQUESTED;
        myCancel();
    }

    /**
     * Optional subclass hook for the cancel() method.
     * Should typically free all the subclass' resources, i.e. requests, threads etc..
     */
    protected void myCancel() { }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
