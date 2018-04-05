// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filters.cors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.jdisc.http.filters.cors.CorsLogic.createCorsResponseHeaders;

/**
 * Security request filters should extend this base class to ensure that CORS header are included in the response of a rejected request.
 * This is required as response filter chains are not executed when a request is rejected in a request filter.
 *
 * @author bjorncs
 */
public abstract class CorsSecurityRequestFilterBase implements SecurityRequestFilter {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Set<String> allowedUrls;

    protected CorsSecurityRequestFilterBase(CorsSecurityFilterConfig config) {
        this(new HashSet<>(config.allowedUrls()));
    }

    protected CorsSecurityRequestFilterBase(Set<String> allowedUrls) {
        this.allowedUrls = allowedUrls;
    }

    @Override
    public final void filter(DiscFilterRequest request, ResponseHandler handler) {
        filter(request)
                .ifPresent(errorResponse -> sendErrorResponse(request, errorResponse, handler));
    }

    protected abstract Optional<ErrorResponse> filter(DiscFilterRequest request);

    private void sendErrorResponse(DiscFilterRequest request,
                                   ErrorResponse errorResponse,
                                   ResponseHandler responseHandler) {
        Response response = new Response(errorResponse.statusCode);
        addHeaders(request, response);
        writeResponse(errorResponse, responseHandler, response);
    }

    private void addHeaders(DiscFilterRequest request, Response response) {
        createCorsResponseHeaders(request.getHeader("Origin"), allowedUrls)
                .forEach(response.headers()::add);
        response.headers().add("Content-Type", "application/json");
    }

    private void writeResponse(ErrorResponse errorResponse, ResponseHandler responseHandler, Response response) {
        ObjectNode errorMessage = mapper.createObjectNode();
        errorMessage.put("message", errorResponse.message);
        try (FastContentWriter writer = ResponseDispatch.newInstance(response).connectFastWriter(responseHandler)) {
            writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorMessage));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected static class ErrorResponse {
        final int statusCode;
        final String message;

        public ErrorResponse(int statusCode, String message) {
            this.statusCode = statusCode;
            this.message = message;
        }
    }
}
