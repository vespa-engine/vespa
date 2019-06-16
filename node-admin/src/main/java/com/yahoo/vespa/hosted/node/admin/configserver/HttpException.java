// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.hosted.node.admin.nodeadmin.ConvergenceException;
import org.apache.http.NoHttpResponseException;

import javax.ws.rs.core.Response;
import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * @author hakonhall
 */
@SuppressWarnings("serial")
public class HttpException extends ConvergenceException {

    private final boolean isRetryable;

    private HttpException(int statusCode, String message, boolean isRetryable) {
        super("HTTP status code " + statusCode + ": " + message);
        this.isRetryable = isRetryable;
    }

    private HttpException(Response.Status status, String message, boolean isRetryable) {
        super(status.toString() + " (" + status.getStatusCode() + "): " + message);
        this.isRetryable = isRetryable;
    }

    private HttpException(String message) {
        super(message);
        this.isRetryable = false;
    }

    boolean isRetryable() {
        return isRetryable;
    }

    /**
     * Returns on success.
     * @throws HttpException for all non-expected status codes.
     */
    static void handleStatusCode(int statusCode, String message) {
        Response.Status status = Response.Status.fromStatusCode(statusCode);
        if (status == null) {
            throw new HttpException(statusCode, message, true);
        }

        switch (status.getFamily()) {
            case SUCCESSFUL: return;
            case CLIENT_ERROR:
                switch (status) {
                    case FORBIDDEN:
                        throw new ForbiddenException(message);
                    case NOT_FOUND:
                        throw new NotFoundException(message);
                    case CONFLICT:
                        // A response body is assumed to be present, and
                        // will later be interpreted as an error.
                        return;
                }
                throw new HttpException(status, message, false);
        }

        // Other errors like server-side errors are assumed to be retryable.
        throw new HttpException(status, message, true);
    }

    /**
     * Returns {@link HttpException} if the given Throwable is of a known and well understood error or
     * a RuntimeException with the given exception as cause otherwise.
     */
    public static RuntimeException handleException(String prefix, Throwable t) {
        for (; t != null; t = t.getCause()) {
            if (t instanceof SocketException ||
                t instanceof SocketTimeoutException ||
                t instanceof NoHttpResponseException ||
                t instanceof EOFException)
                return new HttpException(prefix + t.getMessage());
        }

        return new RuntimeException(prefix, t);
    }

    public static class NotFoundException extends HttpException {
        public NotFoundException(String message) {
            super(Response.Status.NOT_FOUND, message, false);
        }
    }

    public static class ForbiddenException extends HttpException {
        public ForbiddenException(String message) {
            super(Response.Status.FORBIDDEN, message, false);
        }
    }
}
