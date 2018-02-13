// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;

/**
 * @author bjorncs
 */
public class SecurityFilterUtils {
    private static final ObjectMapper mapper = new ObjectMapper();

    private SecurityFilterUtils() {}

    public static void sendErrorResponse(ResponseHandler responseHandler, int statusCode, String message) {
        Response response = new Response(statusCode);
        response.headers().put("Content-Type", "application/json");
        ObjectNode errorMessage = mapper.createObjectNode();
        errorMessage.put("message", message);
        try (FastContentWriter writer = ResponseDispatch.newInstance(response).connectFastWriter(responseHandler)) {
            writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorMessage));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
