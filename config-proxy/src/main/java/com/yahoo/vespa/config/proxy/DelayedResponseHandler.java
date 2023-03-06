// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.yolean.Exceptions;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.protect.Process.logAndDie;

/**
 * The run method of this class is executed periodically to return delayed responses
 * (requests use long polling, so config proxy needs to return a response when they time out).
 *
 * @author hmusum
 */
public class DelayedResponseHandler implements Runnable {

    private final static Logger log = Logger.getLogger(DelayedResponseHandler.class.getName());

    private final DelayedResponses delayedResponses;
    private final MemoryCache memoryCache;
    private final ResponseHandler responseHandler;
    private final AtomicLong sentResponses = new AtomicLong();

    DelayedResponseHandler(DelayedResponses delayedResponses, MemoryCache memoryCache, ResponseHandler responseHandler) {
        this.delayedResponses = delayedResponses;
        this.memoryCache = memoryCache;
        this.responseHandler = responseHandler;
    }

    @Override
    public void run() {
        checkDelayedResponses();
    }

    void checkDelayedResponses() {
        try {
            long start = System.currentTimeMillis();
            log.log(Level.FINEST, () -> "Running DelayedResponseHandler. There are " + delayedResponses.size() +
                    " delayed responses. First one is " + delayedResponses.responses().peek());
            DelayedResponse response;
            while ((response = delayedResponses.responses().poll()) != null) {
                JRTServerConfigRequest request = response.getRequest();
                ConfigCacheKey cacheKey = new ConfigCacheKey(request.getConfigKey(), request.getRequestDefMd5());
                Optional<RawConfig> config = memoryCache.get(cacheKey);
                if (config.isPresent()) {
                    responseHandler.returnOkResponse(request, config.get());
                    sentResponses.incrementAndGet();
                } else {
                    log.log(Level.INFO, "Timed out (timeout " + request.getTimeout() + ") getting config " +
                            request.getConfigKey() + ", will retry");
                }
            }
            log.log(Level.FINEST, () -> "Finished running DelayedResponseHandler. " + sentResponses.get() +
                    " delayed responses sent in " + (System.currentTimeMillis() - start) + " ms");
        } catch (Exception e) {  // To avoid thread throwing exception and executor never running this again
            log.log(Level.WARNING, "Got exception in DelayedResponseHandler: " + Exceptions.toMessageString(e));
        } catch (Throwable e) {
            logAndDie("Got error in DelayedResponseHandler, exiting: " + Exceptions.toMessageString(e));
        }
    }

    public long sentResponses() { return sentResponses.get(); }

}
