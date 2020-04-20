// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.log.LogLevel;

import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A base class for {@link SecurityRequestFilter} implementations that renders an error response as JSON.
 *
 * @author bjorncs
 */
public abstract class JsonSecurityRequestFilterBase extends AbstractComponent implements SecurityRequestFilter {

    private static final Logger log = Logger.getLogger(JsonSecurityRequestFilterBase.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public final void filter(DiscFilterRequest request, ResponseHandler handler) {
        filter(request)
                .ifPresent(errorResponse -> writeResponse(request, errorResponse, handler));
    }

    protected abstract Optional<ErrorResponse> filter(DiscFilterRequest request);

    private void writeResponse(DiscFilterRequest request, ErrorResponse error, ResponseHandler responseHandler) {
        ObjectNode errorMessage = mapper.createObjectNode();
        errorMessage.put("code", error.errorCode);
        errorMessage.put("message", error.message);
        error.response.headers().put("Content-Type", "application/json"); // Note: Overwrites header if already exists
        error.response.headers().put("Cache-Control", "must-revalidate,no-cache,no-store");
        log.log(LogLevel.DEBUG, () -> String.format("Error response for '%s': statusCode=%d, errorCode=%d, message='%s'",
                                                    request, error.response.getStatus(), error.errorCode, error.message));
        try (FastContentWriter writer = ResponseDispatch.newInstance(error.response).connectFastWriter(responseHandler)) {
            writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorMessage));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * An error response that contains a {@link Response}, error code and message.
     * The error code and message will be rendered as JSON fields with name 'code' and 'message' respectively.
     */
    protected static class ErrorResponse {
        private final Response response;
        private final int errorCode;
        private final String message;

        public ErrorResponse(Response response, int errorCode, String message) {
            this.response = response;
            this.errorCode = errorCode;
            this.message = message;
        }

        public ErrorResponse(Response response, String message) {
            this(response, response.getStatus(), message);
        }

        public ErrorResponse(int httpStatusCode, int errorCode, String message) {
            this(new Response(httpStatusCode), errorCode, message);
        }

        public ErrorResponse(int httpStatusCode, String message) {
            this(new Response(httpStatusCode), message);
        }

        public Response getResponse() {
            return response;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }

    }
}
