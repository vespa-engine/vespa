// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// -*- mode: java; folded-file: t; c-basic-offset: 4 -*-
//
//
package com.yahoo.prelude.fastsearch.test.fs4mock;


import com.yahoo.prelude.ConfigurationException;


/**
 * Thread-wrapper for MockFDispatch
 *
 * @author  <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 */
public class DispatchThread extends Thread {
    int listenPort;
    long replyDelay;
    long byteDelay;
    MockFDispatch dispatch;
    Object barrier = new Object();

    /**
     * Instantiate MockFDispatch; if the wanted port is taken we
     * bump the port number.  Note that the delays are not
     * accurate: in reality they will be significantly longer for
     * low values.
     *
     * @param listenPort Wanted port number, note that this may be
     *                   bumped if someone is already running something
     *                   on this port, so it is a starting point for
     *                   scanning only
     * @param replyDelay how many milliseconds we should delay when
     *                   replying
     * @param byteDelay  how many milliseconds we delay for each byte
     *                   written
     */

    public DispatchThread(int listenPort, long replyDelay, long byteDelay) {
        this.listenPort = listenPort;
        this.replyDelay = replyDelay;
        this.byteDelay = byteDelay;
        dispatch = new MockFDispatch(listenPort, replyDelay, byteDelay);
        dispatch.setBarrier(barrier);
    }

    /**
     * Run the MockFDispatch and anticipate multiple instances of
     * same running.
     */
    public void run() {
        int maxTries = 20;
        // the following section is here to make sure that this
        // test is somewhat robust, ie. if someone is already
        // listening to the port in question, we'd like to NOT
        // fail, but keep probing until we find a port we can use.
        boolean up = false;

        while ((!up) && (maxTries-- != 0)) {
            try {
                dispatch.run();
                up = true;
            } catch (ConfigurationException e) {
                listenPort++;
                dispatch.setListenPort(listenPort);
            }
        }
    }

    /**
     * Wait until MockFDispatch is ready to accept connections
     * or we time out and indicate which of the two outcomes it was.
     *
     * @return If we time out we return <code>false</code>.  Else we
     *         return <code>true</code>
     *
     */
    public boolean waitOnBarrier(long timeout) throws InterruptedException {
        long start = System.currentTimeMillis();

        synchronized (barrier) {
            barrier.wait(timeout);
        }
        long diff = System.currentTimeMillis() - start;

        return (diff < timeout);
    }

    /**
     * Return the port on which the MockFDispatch actually listens.
     * use this instead of assuming where it is since, if more than
     * one application tries to use the port we've assigned to it
     * we might have to up the port number.
     *
     * @return port number of active MockFDispatch instance
     *
     */
    public int listenPort() {
        return listenPort;
    }
}
