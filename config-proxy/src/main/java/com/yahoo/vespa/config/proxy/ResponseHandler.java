// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.proxy.ConfigProxyRpcServer.TRACELEVEL;

/**
 * An RPC server that handles config and file distribution requests.
 *
 * @author hmusum
 */
public class ResponseHandler  {

    private final Optional<AtomicLong> sentResponses;

    public ResponseHandler() {
        this(false);
    }

    // For testing only
    ResponseHandler(boolean trackResponses) {
        sentResponses = trackResponses ? Optional.of(new AtomicLong()) : Optional.empty();
    }

    private final static Logger log = Logger.getLogger(ResponseHandler.class.getName());

    public void returnOkResponse(JRTServerConfigRequest request, RawConfig config) {
        request.getRequestTrace().trace(TRACELEVEL, "Config proxy returnOkResponse()");
        request.addOkResponse(config.getPayload(),
                              config.getGeneration(),
                              config.applyOnRestart(),
                              config.getPayloadChecksums());
        log.log(Level.FINE, () -> "Return response: " + request.getShortDescription() + ",config checksums=" + config.getPayloadChecksums() +
                ",generation=" + config.getGeneration());
        log.log(Level.FINEST, () -> "Config payload in response for " + request.getShortDescription() + ":" + config.getPayload());


        // TODO Catch exception for now, since the request might have been returned in CheckDelayedResponse
        // TODO Move logic so that all requests are returned in CheckDelayedResponse
        try {
            request.getRequest().returnRequest();
        } catch (IllegalStateException e) {
            log.log(Level.FINE, () -> "Something bad happened when sending response for '" + request.getShortDescription() + "':" + e.getMessage());
        }
        sentResponses.ifPresent(AtomicLong::getAndIncrement);
    }

    public void returnErrorResponse(JRTServerConfigRequest request, int errorCode, String message) {
        request.getRequestTrace().trace(TRACELEVEL, "Config proxy returnErrorResponse()");
        request.addErrorResponse(errorCode, message);
        request.getRequest().returnRequest();
    }

    public long sentResponses() { return sentResponses.map(AtomicLong::get).orElse(0L); }

}
