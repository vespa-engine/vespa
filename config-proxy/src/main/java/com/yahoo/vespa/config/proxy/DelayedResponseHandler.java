// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.RawConfig;

import java.util.Date;
import java.util.logging.Logger;

/**
 * The run method of this class is executed periodically to return delayed responses
 * (long polling requests that are about to time out and needs to be returned).
 *
 * @author hmusum
 */
public class DelayedResponseHandler implements Runnable {

    private final static Logger log = Logger.getLogger(DelayedResponseHandler.class.getName());

    private final DelayedResponses delayedResponses;
    private final MemoryCache memoryCache;
    private final RpcServer rpcServer;

    DelayedResponseHandler(DelayedResponses delayedResponses, MemoryCache memoryCache, RpcServer rpcServer) {
        this.delayedResponses = delayedResponses;
        this.memoryCache = memoryCache;
        this.rpcServer = rpcServer;
    }

    @Override
    public void run() {
        checkDelayedResponses();
    }

    void checkDelayedResponses() {
        try {
            long start = System.currentTimeMillis();
            if (log.isLoggable(LogLevel.SPAM)) {
                log.log(LogLevel.SPAM, "Running DelayedResponseHandler. There are " + delayedResponses.size() + " delayed responses. First one is " + delayedResponses.responses().peek());
            }
            DelayedResponse response;
            int i = 0;

            while ((response = delayedResponses.responses().poll()) != null) {
                if (log.isLoggable(LogLevel.DEBUG)) {
                    log.log(LogLevel.DEBUG, "Returning with response that has return time " + new Date(response.getReturnTime()));
                }
                JRTServerConfigRequest request = response.getRequest();
                ConfigCacheKey cacheKey = new ConfigCacheKey(request.getConfigKey(), request.getConfigKey().getMd5());
                RawConfig config = memoryCache.get(cacheKey);
                if (config != null) {
                    rpcServer.returnOkResponse(request, config);
                    i++;
                } else {
                    log.log(LogLevel.WARNING, "No config found for " + request.getConfigKey() + " within timeout, will retry");
                }
            }
            if (log.isLoggable(LogLevel.SPAM)) {
                log.log(LogLevel.SPAM, "Finished running DelayedResponseHandler. " + i + " delayed responses sent in " +
                        (System.currentTimeMillis() - start) + " ms");
            }
        } catch (Exception e) {  // To avoid thread throwing exception and executor never running this again
            log.log(LogLevel.WARNING, "Got exception in DelayedResponseHandler: " + Exceptions.toMessageString(e));
        } catch (Throwable e) {
            com.yahoo.protect.Process.logAndDie("Got error in DelayedResponseHandler, exiting: " + Exceptions.toMessageString(e));
        }
    }
}
