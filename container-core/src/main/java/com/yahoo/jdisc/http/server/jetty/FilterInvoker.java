// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.ImplementedBy;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;

/**
 * Separate interface since DiscFilterRequest/Response and Security filter chains are not accessible in this bundle
 */
@ImplementedBy(UnsupportedFilterInvoker.class)
public interface FilterInvoker {
    HttpServletRequest invokeRequestFilterChain(RequestFilter requestFilterChain,
                                                URI uri,
                                                HttpServletRequest httpRequest,
                                                ResponseHandler responseHandler);

    void invokeResponseFilterChain(
            ResponseFilter responseFilterChain,
            URI uri,
            HttpServletRequest request,
            HttpServletResponse response);
}
