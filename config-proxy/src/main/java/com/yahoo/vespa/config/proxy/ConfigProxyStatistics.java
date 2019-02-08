// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.log.LogLevel;
import com.yahoo.log.event.Event;

/**
 * Statistics/metrics for config proxy.
 * //TODO Use metrics framework
 *
 * @author hmusum
 */
class ConfigProxyStatistics implements Runnable {
    static final long defaultEventInterval = 5 * 60; // in seconds

    private final long eventInterval; // in seconds
    private boolean stopped;
    private long lastRun = System.currentTimeMillis();

    /* Number of RPC getConfig requests */
    private long rpcRequests = 0;
    private long processedRequests = 0;
    private long errors = 0;
    private long delayedResponses = 0;

    ConfigProxyStatistics() {
        this(defaultEventInterval);
    }

    ConfigProxyStatistics(long eventInterval) {
        this.eventInterval = eventInterval;
    }

    // Send events every eventInterval seconds
    public void run() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                ProxyServer.log.log(LogLevel.WARNING, e.getMessage());
            }
            if (stopped) {
                return;
            }
            ProxyServer.log.log(LogLevel.SPAM, "Running ConfigProxyStatistics");
            // Only send events every eventInterval seconds
            if ((System.currentTimeMillis() - lastRun) > eventInterval * 1000) {
                lastRun = System.currentTimeMillis();
                sendEvents();
            }
        }
    }

    private void sendEvents() {
        Event.count("rpc_requests", rpcRequests());
        Event.count("processed_messages", processedRequests());
        Event.count("errors", errors());
        Event.value("delayed_responses", delayedResponses());
    }

    void stop() {
        stopped = true;
    }

    Long getEventInterval() {
        return eventInterval;
    }

    void incRpcRequests() {
        rpcRequests++;
    }

    void incProcessedRequests() {
        processedRequests++;
    }

    void incErrorCount() {
        errors++;
    }

    long processedRequests() {
        return processedRequests;
    }

    long rpcRequests() {
        return rpcRequests;
    }

    long errors() {
        return errors;
    }

    long delayedResponses() {
        return delayedResponses;
    }

    void delayedResponses(long count) {
        delayedResponses = count;
    }

    void decDelayedResponses() {
        delayedResponses--;
    }
}
