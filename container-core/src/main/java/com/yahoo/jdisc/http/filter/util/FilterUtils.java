// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.server.jetty.RequestUtils;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

/**
 * Helper methods for auth0/okta request filters.
 *
 * @author valerijf
 */
public class FilterUtils {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static boolean isDifferentOrigin(DiscFilterRequest request) {
        try {
            String origin = request.getHeader("Origin");
            if (origin != null && !URI.create(origin).getHost().equals(request.getServerName()))
                return true;
        } catch (RuntimeException ignored) { }
        return false;
    }

    public static void sendRedirectResponse(ResponseHandler handler, List<Cookie> cookies, String location) {
        Response response = createResponse(Response.Status.FOUND, cookies);
        response.headers().add("Location", location);
        handler.handleResponse(response).close(null);
    }

    public static void sendMessageResponse(ResponseHandler handler, List<Cookie> cookies, int code, String message) {
        Response response = createResponse(code, cookies);
        ContentChannel contentChannel = handler.handleResponse(response);
        try {
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

    public static URI createUriFromRequest(DiscFilterRequest request, String path, Optional<String> hostOverride) {
        try {
            // Prefer local port as observed by client over local listen port
            int port = Optional.ofNullable((Integer)request.getAttribute(RequestUtils.JDICS_REQUEST_PORT))
                    .orElse(request.getUri().getPort());
            String host = hostOverride.orElse(request.getServerName());
            return new URI(request.getScheme(), null, host, port, path, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
