// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.concurrent.DelayQueue;
import java.util.logging.Logger;

/**
 * Updates clients subscribing to config when config changes or the
 * timeout they have specified has elapsed.
 *
 * @author hmusum
 */
class ClientUpdater {
    private final static Logger log = Logger.getLogger(ClientUpdater.class.getName());

    private final MemoryCache memoryCache;
    private final ConfigProxyStatistics statistics;
    private final RpcServer rpcServer;
    private final DelayedResponses delayedResponses;
    private final Mode mode;

    ClientUpdater(MemoryCache memoryCache,
                  RpcServer rpcServer,
                  ConfigProxyStatistics statistics,
                  DelayedResponses delayedResponses,
                  Mode mode) {
        this.memoryCache = memoryCache;
        this.rpcServer = rpcServer;
        this.statistics = statistics;
        this.delayedResponses = delayedResponses;
        this.mode = mode;
    }

    /**
     * This method will be called when a response with changed config is received from upstream
     * (content or generation has changed) or the server timeout has elapsed.
     * Updates the cache with the returned config.
     *
     * @param config new config
     */
    void updateSubscribers(RawConfig config) {
        // ignore updates if we are in one of these modes (we will then only serve from cache).
        if (!mode.requiresConfigSource()) {
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Not updating " + config.getKey() + "," + config.getGeneration() +
                        ", since we are in '" + mode + "' mode");
            }
            return;
        }
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Config updated for " + config.getKey() + "," + config.getGeneration());
        }
        memoryCache.put(config);
        sendResponse(config);
    }

    private void sendResponse(RawConfig config) {
        if (config.isError()) { statistics.incErrorCount(); }
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Sending response for " + config.getKey() + "," + config.getGeneration());
        }
        DelayQueue<DelayedResponse> responseDelayQueue = delayedResponses.responses();
        if (log.isLoggable(LogLevel.SPAM)) {
            log.log(LogLevel.SPAM, "Delayed response queue: " + responseDelayQueue);
        }
        if (responseDelayQueue.size() == 0) {
            log.log(LogLevel.DEBUG, "There exists no matching element on delayed response queue for " + config.getKey());
            return;
        } else {
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Delayed response queue has " + responseDelayQueue.size() + " elements");
            }
        }
        DelayedResponse[] responses = new DelayedResponse[1];
        responses = responseDelayQueue.toArray(responses);
        boolean found = false;
        if (responses.length > 0) {
            for (DelayedResponse response : responses) {
                JRTServerConfigRequest request = response.getRequest();
                if (request.getConfigKey().equals(config.getKey())) {
                    if (!delayedResponses.remove(response)) {
                        if (log.isLoggable(LogLevel.DEBUG)) {
                            log.log(LogLevel.DEBUG, "Could not remove " + config.getKey() + " from delayed delayedResponses queue, already removed");
                        }
                        continue;
                    }
                    found = true;
                    if (log.isLoggable(LogLevel.DEBUG)) {
                        log.log(LogLevel.DEBUG, "Call returnOkResponse for " + config.getKey() + "," + config.getGeneration());
                    }
                    rpcServer.returnOkResponse(request, config);
                }
            }

        }
        if (!found) {
            log.log(LogLevel.DEBUG, "Found no recipient for " + config.getKey() + " in delayed response queue");
        }
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Finished updating config for " + config.getKey() + "," + config.getGeneration());
        }
    }

    // TODO: Remove, temporary until MapBackedConfigSource has been refactored
    public MemoryCache getMemoryCache() {
        return memoryCache;
    }
}
