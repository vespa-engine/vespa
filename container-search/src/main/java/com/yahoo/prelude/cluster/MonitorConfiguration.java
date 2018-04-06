// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster;

/**
 * The configuration of a cluster monitor instance
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class MonitorConfiguration {

    /**
     * The interval in ms between consecutive checks of the monitored nodes
     */
    private final long checkInterval = 100;

    /**
     * The number of milliseconds to attempt to complete a request before giving
     * up
     */
    private long requestTimeout = 2700;

    public MonitorConfiguration(final QrMonitorConfig config) {
        requestTimeout = config.requesttimeout();
    }

    /**
     * Returns the interval between each ping of idle or failing nodes Default
     * is 1000ms
     */
    public long getCheckInterval() {
        return checkInterval;
    }

    /**
     * Sets the number of milliseconds to attempt to service a request (at
     * different nodes) before giving up.
     */
    public void setRequestTimeout(final long timeout) {
        requestTimeout = timeout;
    }

    /**
     * Returns the number of milliseconds to attempt to service a request (at
     * different nodes) before giving up. Default is 2700 ms.
     */
    public long getRequestTimeout() {
        return requestTimeout;
    }

}
