// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.service.CurrentContainer;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
class WebSocketRequestFactory {

    public static HttpRequest newJDiscRequest(final CurrentContainer container,
                                              final ServletUpgradeRequest servletRequest) {
        return HttpRequest.newServerRequest(
                container,
                servletRequest.getRequestURI(),
                HttpRequest.Method.valueOf(servletRequest.getMethod()),
                HttpRequest.Version.fromString(servletRequest.getHttpVersion()),
                new InetSocketAddress(servletRequest.getRemoteAddress(), servletRequest.getRemotePort()));
    }

    public static void copyHeaders(final ServletUpgradeRequest from, final Request to) {
        to.headers().addAll(from.getHeaders());
    }

    public static void copyHeaders(final Response from, final ServletUpgradeResponse to) {
        for (final Map.Entry<String, String> entry : from.headers().entries()) {
            to.addHeader(entry.getKey(), entry.getValue());
        }
    }
}
