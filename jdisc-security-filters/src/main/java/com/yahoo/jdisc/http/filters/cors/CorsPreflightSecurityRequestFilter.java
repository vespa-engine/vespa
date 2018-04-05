// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filters.cors;

import com.google.inject.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.yolean.chain.Provides;

import java.util.HashSet;
import java.util.Set;

import static com.yahoo.jdisc.http.HttpRequest.Method.OPTIONS;
import static com.yahoo.jdisc.http.filters.cors.CorsLogic.createCorsPreflightResponseHeaders;

/**
 * <p>
 * This filter makes sure we respond as quickly as possible to CORS pre-flight requests
 * which browsers transmit before the Hosted Vespa dashboard code is allowed to send a "real" request.
 * </p>
 * <p>
 * An "Access-Control-Max-Age" header is added so that the browser will cache the result of this pre-flight request,
 * further improving the responsiveness of the Hosted Vespa dashboard application.
 * </p>
 * <p>
 * Runs after all standard security request filters, but before BouncerFilter, as the browser does not send
 * credentials with pre-flight requests.
 * </p>
 *
 * @author andreer
 * @author gv
 * @author bjorncs
 */
@Provides("CorsPreflightSecurityRequestFilter")
public class CorsPreflightSecurityRequestFilter implements SecurityRequestFilter {
    private final Set<String> allowedUrls;

    @Inject
    public CorsPreflightSecurityRequestFilter(CorsSecurityFilterConfig config) {
        this.allowedUrls = new HashSet<>(config.allowedUrls());
    }

    @Override
    public void filter(DiscFilterRequest discFilterRequest, ResponseHandler responseHandler) {
        String origin = discFilterRequest.getHeader("Origin");

        if (!discFilterRequest.getMethod().equals(OPTIONS.name()))
            return;

        HttpResponse response = HttpResponse.newInstance(Response.Status.OK);

        createCorsPreflightResponseHeaders(origin, allowedUrls)
                .forEach(response.headers()::put);

        ContentChannel cc = responseHandler.handleResponse(response);
        cc.close(null);
    }
}
