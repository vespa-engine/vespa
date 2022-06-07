// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

/**
 * The configuration of a cluster monitor instance
 *
 * @author bratseth
 */
public class MonitorConfiguration  {

    /** The interval in ms between consecutive checks of the monitored nodes */
    private final long checkInterval = 1000;

    /** The number of milliseconds to attempt to complete a request before giving up */
    private final long requestTimeout = 980;

    /** The number of milliseconds a node is allowed to fail before we mark it as not working */
    private final long failLimit = 5000;

    /** Returns the interval between each ping of idle or failing nodes. Default is 1000 ms. */
    public long getCheckInterval() { return checkInterval; }

    /**
     * Returns the number of milliseconds to attempt to service a request
     * (at different nodes) before giving up. Default is 5000 ms.
     */
    public long getRequestTimeout() { return requestTimeout; }

    /**
     * Returns the number of milliseconds a node is allowed to fail before we
     * mark it as not working
     */
    public long getFailLimit() { return failLimit; }

    @Override
    public String toString() {
        return "monitor configuration [" +
               "checkInterval: " + checkInterval +
               " requestTimeout " + requestTimeout +
               " failLimit " + failLimit +
               "]";
    }

}
