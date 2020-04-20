// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

/**
 * The configuration of a cluster monitor instance
 *
 * @author bratseth
 */
public class MonitorConfiguration  {

    /** The interval in ms between consecutive checks of the monitored nodes */
    private long checkInterval=1000;

    /** The number of milliseconds to attempt to complete a request before giving up */
    private final long requestTimeout = 980;

    /** The number of milliseconds a node is allowed to fail before we mark it as not working */
    private long failLimit = 5000;

    /** Sets the interval between each ping of idle or failing nodes. Default is 1000 ms. */
    public void setCheckInterval(long intervalMs) { this.checkInterval = intervalMs; }

    /** Returns the interval between each ping of idle or failing nodes. Default is 1000 ms. */
    public long getCheckInterval() { return checkInterval; }

    /**
     * Sets the number of times a failed node must respond before it is put  back in service. Default is 3.
     *
     * @deprecated will go away in Vespa 8
     */
    @Deprecated // TODO: Remove on Vespa 8
    public void setResponseAfterFailLimit(int responseAfterFailLimit) { }

    /**
     * Sets the number of ms a node (failing or working) is allowed to stay idle before it is pinged. Default is 3000.
     *
     * @deprecated Will go away in Vespa 8
     */
    @Deprecated // TODO: Remove on Vespa 8
    public void setIdleLimit(int idleLimit) { }

    /**
     * Gets the number of ms a node (failing or working) is allowed to stay idle before it is pinged. Default is 3000.
     *
     * @deprecated Will go away in Vespa 8
     */
    @Deprecated // TODO: Remove on Vespa 8
    public long getIdleLimit() {
        return 3000;
    }

    /**
     * Returns the number of milliseconds to attempt to service a request
     * (at different nodes) before giving up. Default is 5000 ms.
     */
    public long getRequestTimeout() { return requestTimeout; }

    /**
     * Sets the number of milliseconds a node is allowed to fail before we
     * mark it as not working
     */
    public void setFailLimit(long failLimit) { this.failLimit=failLimit; }

    /**
     * Returns the number of milliseconds a node is allowed to fail before we
     * mark it as not working
     */
    public long getFailLimit() { return failLimit; }

    /**
     * The number of times a node must fail in one hour to be placed
     * in quarantine.  Once in quarantine it won't be put back in
     * productuion before quarantineTime has expired even if it is
     * working. Default is 3
     *
     * @deprecated Will go away in Vespa 8
     */
    @Deprecated // TODO: Remove on Vespa 8
    public void setFailQuarantineLimit(int failQuarantineLimit) { }

    /**
     * The number of ms an unstable node is quarantined. Default is 100*60*60
     *
     * @deprecated Will go away in Vespa 8
     */
    @Deprecated // TODO: Remove on Vespa 8
    public void setQuarantineTime(long quarantineTime) { }

    public String toString() {
        return "monitor configuration [" +
               "checkInterval: " + checkInterval +
               " requestTimeout " + requestTimeout +
               " failLimit " + failLimit +
               "]";
    }

}
