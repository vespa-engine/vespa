// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.restapi.ErrorResponse;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for creating error responses.
 *
 * @author mpolden
 */
public class ErrorResponses {

    private ErrorResponses() {}

    /**
     * Returns a response for a failing request containing an unique request ID. Details of the error are logged through
     * given logger.
     */
    public static ErrorResponse logThrowing(HttpRequest request, Logger logger, Throwable t) {
        String requestId = UUID.randomUUID().toString();
        logger.log(Level.SEVERE, "Unexpected error handling '" + request.getUri() + "' (request ID: " +
                                  requestId + ")", t);
        return ErrorResponse.internalServerError("Unexpected error occurred (request ID: " + requestId + ")");
    }

}
