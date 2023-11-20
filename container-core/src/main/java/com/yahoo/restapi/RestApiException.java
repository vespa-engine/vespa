// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;

import java.util.function.Function;

/**
 * A {@link RuntimeException} that represents a http response.
 *
 * @author bjorncs
 */
public class RestApiException extends RuntimeException {
    private final int statusCode;
    private final HttpResponse response;

    public RestApiException(int statusCode, String errorType, String message) {
        this(new ErrorResponse(statusCode, errorType, message), message, null);
    }

    public RestApiException(HttpResponse response, String message) {
        this(response, message, null);
    }

    public RestApiException(int statusCode, String errorType, String message, Throwable cause) {
        this(new ErrorResponse(statusCode, errorType, message), message, cause);
    }

    public RestApiException(HttpResponse response, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = response.getStatus();
        this.response = response;
    }

    private RestApiException(Function<String, HttpResponse> responseFromMessage, String message, Throwable cause) {
        this(responseFromMessage.apply(message), message, cause);
    }

    public int statusCode() { return statusCode; }
    public HttpResponse response() { return response; }

    public static class NotFound extends RestApiException {
        public NotFound() { this(null, null); }
        public NotFound(HttpRequest request) { this("Nothing at '" + request.getUri().getRawPath() + "'", null); }
        public NotFound(Throwable cause) { this(cause.getMessage(), cause); }
        public NotFound(String message) { this(message, null); }
        public NotFound(String message, Throwable cause) { super(ErrorResponse::notFoundError, message, cause); }
    }

    public static class MethodNotAllowed extends RestApiException {
        public MethodNotAllowed() { super(ErrorResponse::methodNotAllowed, "Method not allowed", null); }
        public MethodNotAllowed(HttpRequest request) {
            super(ErrorResponse::methodNotAllowed, "Method '" + request.getMethod().name() + "' is not allowed at '" +
                                                   request.getUri().getRawPath() + "'", null);
        }
    }

    public static class BadRequest extends RestApiException {
        public BadRequest() { this("Bad request"); }
        public BadRequest(String message) { this(message, null); }
        public BadRequest(Throwable cause) { this(cause.getMessage(), cause); }
        public BadRequest(String message, Throwable cause) { super(ErrorResponse::badRequest, message, cause); }
    }

    public static class InternalServerError extends RestApiException {
        public InternalServerError(String message) { this(message, null); }
        public InternalServerError(Throwable cause) { this(cause.getMessage(), cause); }
        public InternalServerError(String message, Throwable cause) { super(ErrorResponse::internalServerError, message, cause); }
    }

    public static class Forbidden extends RestApiException {
        public Forbidden() { this("Forbidden"); }
        public Forbidden(String message) { super(ErrorResponse::forbidden, message, null); }
        public Forbidden(String message, Throwable cause) { super(ErrorResponse::forbidden, message, cause); }
    }

    public static class Conflict extends RestApiException {
        public Conflict() { this("Conflict", null); }
        public Conflict(String message) { this(message, null); }
        public Conflict(String message, Throwable cause) { super(ErrorResponse::conflict, message, cause); }
    }

    public static class Unauthorized extends RestApiException {
        public Unauthorized() { this("Unauthorized", null); }
        public Unauthorized(String message) { this(message, null); }
        public Unauthorized(String message, Throwable cause) { super(ErrorResponse::unauthorized, message, cause); }
    }
}
