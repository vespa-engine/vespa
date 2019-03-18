// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.concurrent.DelayQueue;
import java.util.logging.Logger;

/**
 * Updates clients subscribing to config when config changes or the
 * timeout they have specified has elapsed. Not used when in 'memorycache' mode.
 *
 * @author hmusum
 */
class ClientUpdater {

    private final static Logger log = Logger.getLogger(ClientUpdater.class.getName());

    private final ConfigProxyStatistics statistics;
    private final RpcServer rpcServer;
    private final DelayedResponses delayedResponses;

    ClientUpdater(RpcServer rpcServer, ConfigProxyStatistics statistics, DelayedResponses delayedResponses) {
        this.rpcServer = rpcServer;
        this.statistics = statistics;
        this.delayedResponses = delayedResponses;
    }

    /**
     * This method will be called when a response with changed config is received from upstream
     * (content or generation has changed) or the server timeout has elapsed.
     *
     * @param config new config
     */
    void updateSubscribers(RawConfig config) {
        log.log(LogLevel.DEBUG, () -> "Config updated for " + config.getKey() + "," + config.getGeneration());
        if (config.isError()) { statistics.incErrorCount(); }
        DelayQueue<DelayedResponse> responseDelayQueue = delayedResponses.responses();
        log.log(LogLevel.SPAM, () -> "Delayed response queue: " + responseDelayQueue);
        if (responseDelayQueue.size() == 0) {
            log.log(LogLevel.DEBUG, () -> "There exists no matching element on delayed response queue for " + config.getKey());
            return;
        } else {
            log.log(LogLevel.DEBUG, () -> "Delayed response queue has " + responseDelayQueue.size() + " elements");
        }
        boolean found = false;
        for (DelayedResponse response : responseDelayQueue.toArray(new DelayedResponse[0])) {
            JRTServerConfigRequest request = response.getRequest();
            if (request.getConfigKey().equals(config.getKey())
                    // Generation 0 is special, used when returning empty sentinel config
                    && (config.hasNewerGeneration(request) || config.getGeneration() == 0)) {
                if (delayedResponses.remove(response)) {
                    found = true;
                    log.log(LogLevel.DEBUG, () -> "Call returnOkResponse for " + config.getKey() + "," + config.getGeneration());
                    rpcServer.returnOkResponse(request, config);
                } else {
                    log.log(LogLevel.INFO, "Could not remove " + config.getKey() + " from delayedResponses queue, already removed");
                }
            }
        }
        if (!found) {
            log.log(LogLevel.DEBUG, () -> "Found no recipient for " + config.getKey() + " in delayed response queue");
        }
        log.log(LogLevel.DEBUG, () -> "Finished updating config for " + config.getKey() + "," + config.getGeneration());
    }

}
