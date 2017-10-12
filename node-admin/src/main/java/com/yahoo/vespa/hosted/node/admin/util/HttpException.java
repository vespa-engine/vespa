// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import javax.ws.rs.core.Response;

@SuppressWarnings("serial")
public class HttpException extends RuntimeException {
    public static class NotFoundException extends HttpException {
        private static final long serialVersionUID = 4791511887L;
        public NotFoundException(String message) {
            super(Response.Status.NOT_FOUND, message);
        }
    }

    static void throwOnFailure(int statusCode, String message) {
        Response.Status status = Response.Status.fromStatusCode(statusCode);
        if (status == null) {
            throw new HttpException(statusCode, message);
        }

        if (status.getFamily() == Response.Status.Family.SUCCESSFUL) {
            return;
        }

        switch (status) {
            case NOT_FOUND: throw new NotFoundException(message);
            case CONFLICT:
                // A response body is assumed to be present, and
                // will later be interpreted as an error.
                return;
            default:
                throw new HttpException(status, message);
        }
    }

    private HttpException(int statusCode, String message) {
        super("HTTP status code " + statusCode + ": " + message);
    }

    private HttpException(Response.Status status, String message) {
        super(status.toString() + " (" + status.getStatusCode() + "): " + message);
    }
}
