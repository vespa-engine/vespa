// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;

import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
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

    protected ObjectMapper jsonMapper() { return mapper; }

    private void writeResponse(DiscFilterRequest request, ErrorResponse error, ResponseHandler responseHandler) {
        JsonNode json;
        if (error.customJson != null) {
            json = error.customJson;
        } else {
            ObjectNode o = mapper.createObjectNode();
            if (error.errorCodeAsInt != null) o.put("code", error.errorCodeAsInt);
            else if (error.errorCodeAsString != null) o.put("code", error.errorCodeAsString);
            if (error.message != null) o.put("message", error.message);
            json = o;
        }
        error.response.headers().put("Content-Type", "application/json"); // Note: Overwrites header if already exists
        error.response.headers().put("Cache-Control", "must-revalidate,no-cache,no-store");
        try (FastContentWriter writer = ResponseDispatch.newInstance(error.response).connectFastWriter(responseHandler)) {
            String jsonAsStr = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            log.log(Level.FINE, () -> String.format("Error response for '%s': statusCode=%d, json='%s'",
                    request, error.response.getStatus(), jsonAsStr));
            writer.write(jsonAsStr);
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
        private final JsonNode customJson;
        private final Integer errorCodeAsInt;
        private final String errorCodeAsString;
        private final String message;

        private ErrorResponse(Response response, JsonNode customJson, Integer errorCodeAsInt, String errorCodeAsString,
                              String message) {
            this.response = Objects.requireNonNull(response);
            this.customJson = customJson;
            this.errorCodeAsInt = errorCodeAsInt;
            this.errorCodeAsString = errorCodeAsString;
            this.message = message;
        }

        public ErrorResponse(Response response, int errorCode, String message) {
            this(response, null, errorCode, null, message);
        }

        public ErrorResponse(Response response, String message) {
            this(response, response.getStatus(), message);
        }

        public ErrorResponse(int httpStatusCode, int errorCode, String message) {
            this(new Response(httpStatusCode), errorCode, message);
        }

        public ErrorResponse(int httpStatusCode, String errorCode, String message) {
            this(new Response(httpStatusCode), null, null, errorCode, message);
        }

        public ErrorResponse(int httpStatusCode, String message) {
            this(new Response(httpStatusCode), message);
        }

        public ErrorResponse(Response response, JsonNode json) {
            this(response, json, null, null, null);
        }

        public Response getResponse() {
            return response;
        }

        public Optional<String> getMessage() { return Optional.ofNullable(message); }

    }
}
