// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

/**
 * This exception may be thrown from a request handler to fail a request with a given response code and message.
 * It is given some special treatment in {@link ServletResponseController}.
 *
 * @author bakksjo
 */
class RequestException extends RuntimeException {

    private final int responseStatus;

    /**
     * @param responseStatus the response code to use for the http response
     * @param message        exception message
     * @param cause          chained throwable
     */
    public RequestException(final int responseStatus, final String message, final Throwable cause) {
        super(message, cause);
        this.responseStatus = responseStatus;
    }

    /**
     * @param responseStatus the response code to use for the http response
     * @param message        exception message
     */
    public RequestException(final int responseStatus, final String message) {
        super(message);
        this.responseStatus = responseStatus;
    }

    /**
     * Returns the response code to use for the http response.
     */
    public int getResponseStatus() {
        return responseStatus;
    }
}
