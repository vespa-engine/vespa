// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.yolean.chain.Provides;

import java.util.Set;

import static com.yahoo.jdisc.http.HttpRequest.Method.OPTIONS;

/**
 * <p>
 * This filter makes sure we respond as quickly as possible to CORS pre-flight requests
 * which browsers transmit before the Hosted Vespa console code is allowed to send a "real" request.
 * </p>
 * <p>
 * An "Access-Control-Max-Age" header is added so that the browser will cache the result of this pre-flight request,
 * further improving the responsiveness of the Hosted Vespa console.
 * </p>
 * <p>
 * Runs after before any security request filters to avoid CORS errors.
 * </p>
 *
 * @author andreer
 * @author gv
 * @author bjorncs
 */
@Provides("CorsPreflightRequestFilter")
public class CorsPreflightRequestFilter implements SecurityRequestFilter {
    private final Set<String> allowedUrls;

    @Inject
    public CorsPreflightRequestFilter(CorsFilterConfig config) {
        this.allowedUrls = Set.copyOf(config.allowedUrls());
    }

    @Override
    public void filter(DiscFilterRequest discFilterRequest, ResponseHandler responseHandler) {
        if (!discFilterRequest.getMethod().equals(OPTIONS.name()))
            return;

        HttpResponse response = HttpResponse.newInstance(Response.Status.OK);
        String origin = discFilterRequest.getHeader("Origin");
        CorsLogic.createCorsPreflightResponseHeaders(origin, allowedUrls)
                .forEach(response.headers()::put);

        ContentChannel cc = responseHandler.handleResponse(response);
        cc.close(null);
    }
}
