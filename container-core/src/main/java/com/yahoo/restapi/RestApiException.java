// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    public static class NotFoundException extends RestApiException {
        public NotFoundException() { super(ErrorResponse::notFoundError, "Not Found", null); }
    }

    public static class MethodNotAllowed extends RestApiException {
        public MethodNotAllowed() { super(ErrorResponse::methodNotAllowed, "Method not allowed", null); }
        public MethodNotAllowed(HttpRequest request) {
            super(ErrorResponse::methodNotAllowed, "Method '" + request.getMethod().name() + "' is not allowed", null);
        }
    }

    public static class BadRequest extends RestApiException {
        public BadRequest(String message) { super(ErrorResponse::badRequest, message, null); }
        public BadRequest(String message, Throwable cause) { super(ErrorResponse::badRequest, message, cause); }
    }

    public static class InternalServerError extends RestApiException {
        public InternalServerError(String message) { super(ErrorResponse::internalServerError, message, null); }
        public InternalServerError(String message, Throwable cause) { super(ErrorResponse::internalServerError, message, cause); }
    }
}
