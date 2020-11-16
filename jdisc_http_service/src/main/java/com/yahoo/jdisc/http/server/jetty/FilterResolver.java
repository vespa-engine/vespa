// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;

import static com.yahoo.jdisc.http.server.jetty.JDiscHttpServlet.getConnector;

/**
 * Resolve request/response filter (chain) based on {@link FilterBindings}.
 *
 * @author bjorncs
 */
class FilterResolver {

    private final FilterBindings bindings;

    FilterResolver(FilterBindings bindings) {
        this.bindings = bindings;
    }

    Optional<RequestFilter> resolveRequestFilter(HttpServletRequest servletRequest, URI jdiscUri) {
        return bindings.resolveRequestFilter(jdiscUri, getConnector(servletRequest).listenPort())
                .map(bindings::getRequestFilter);
    }

    Optional<ResponseFilter> resolveResponseFilter(HttpServletRequest servletRequest, URI jdiscUri) {
        return bindings.resolveResponseFilter(jdiscUri, getConnector(servletRequest).listenPort())
                .map(bindings::getResponseFilter);
    }
}
