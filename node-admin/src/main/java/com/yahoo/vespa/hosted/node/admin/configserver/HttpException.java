// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import javax.ws.rs.core.Response;
import java.util.Optional;

@SuppressWarnings("serial")
public class HttpException extends RuntimeException {
    public static class NotFoundException extends HttpException {
        private static final long serialVersionUID = 4791511887L;
        public NotFoundException(String message) {
            super(Response.Status.NOT_FOUND, message);
        }
    }

    /**
     * Returns empty on success.
     * Returns an exception if the error is retriable.
     * Throws an exception on a non-retriable error, like 404 Not Found.
     */
    static Optional<HttpException> handleStatusCode(int statusCode, String message) {
        Response.Status status = Response.Status.fromStatusCode(statusCode);
        if (status == null) {
            return Optional.of(new HttpException(statusCode, message));
        }

        switch (status.getFamily()) {
            case SUCCESSFUL: return Optional.empty();
            case CLIENT_ERROR:
                switch (status) {
                    case NOT_FOUND:
                        throw new NotFoundException(message);
                    case CONFLICT:
                        // A response body is assumed to be present, and
                        // will later be interpreted as an error.
                        return Optional.empty();
                }
                throw new HttpException(statusCode, message);
        }

        // Other errors like server-side errors are assumed to be retryable.
        return Optional.of(new HttpException(status, message));
    }

    private HttpException(int statusCode, String message) {
        super("HTTP status code " + statusCode + ": " + message);
    }

    private HttpException(Response.Status status, String message) {
        super(status.toString() + " (" + status.getStatusCode() + "): " + message);
    }
}
