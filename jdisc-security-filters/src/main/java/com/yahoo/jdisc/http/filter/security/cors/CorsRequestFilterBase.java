// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.jdisc.http.filter.security.cors.CorsLogic.createCorsResponseHeaders;

/**
 * Security request filters should extend this base class to ensure that CORS header are included in the response of a rejected request.
 * This is required as response filter chains are not executed when a request is rejected in a request filter.
 *
 * @author bjorncs
 */
public abstract class CorsRequestFilterBase extends JsonSecurityRequestFilterBase {

    private final Set<String> allowedUrls;

    protected CorsRequestFilterBase(CorsFilterConfig config) {
        this(new HashSet<>(config.allowedUrls()));
    }

    protected CorsRequestFilterBase(Set<String> allowedUrls) {
        this.allowedUrls = allowedUrls;
    }

    @Override
    public final Optional<ErrorResponse> filter(DiscFilterRequest request) {
        Optional<ErrorResponse> errorResponse = filterRequest(request);
        errorResponse.ifPresent(response -> addCorsHeaders(request, response.getResponse()));
        return errorResponse;
    }

    protected abstract Optional<ErrorResponse> filterRequest(DiscFilterRequest request);

    private void addCorsHeaders(DiscFilterRequest request, Response response) {
        createCorsResponseHeaders(request.getHeader("Origin"), allowedUrls)
                .forEach(response.headers()::add);
    }

}
