// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.util;

import com.yahoo.json.Jackson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.Cookie;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Helper methods for auth0/okta request filters.
 *
 * @author valerijf
 */
public class FilterUtils {

    public static void sendRedirectResponse(ResponseHandler handler, List<Cookie> cookies, String location) {
        Response response = createResponse(Response.Status.FOUND, cookies);
        response.headers().add("Location", location);
        handler.handleResponse(response).close(null);
    }

    public static void sendMessageResponse(ResponseHandler handler, List<Cookie> cookies, int code, String message) {
        Response response = createResponse(code, cookies);
        ContentChannel contentChannel = handler.handleResponse(response);
        try {
            var mapper = Jackson.mapper();
            ObjectNode jsonNode = mapper.createObjectNode();
            jsonNode.set("message", TextNode.valueOf(message));
            byte[] jsonBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(jsonNode);
            contentChannel.write(ByteBuffer.wrap(jsonBytes), null);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        contentChannel.close(null);
    }

    private static Response createResponse(int code, List<Cookie> cookies) {
        Response response = new Response(code);
        List<String> setCookieHeaders = Cookie.toSetCookieHeaders(cookies);
        response.headers().add("Set-Cookie", setCookieHeaders);
        return response;
    }

}
