// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.http.filter.DiscFilterResponse;
import com.yahoo.jdisc.http.filter.RequestView;
import com.yahoo.jdisc.http.filter.SecurityResponseFilter;
import com.yahoo.vespa.hosted.controller.restapi.filter.config.HttpAccessControlConfig;

import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.restapi.filter.AccessControlHeaders.ACCESS_CONTROL_HEADERS;
import static com.yahoo.vespa.hosted.controller.restapi.filter.AccessControlHeaders.ALLOW_ORIGIN_HEADER;

/**
 * @author gv
 * @author Tony Vaagenes
 */
public class AccessControlResponseFilter extends AbstractResource implements SecurityResponseFilter {

    private final List<String> allowedUrls;

    public AccessControlResponseFilter(HttpAccessControlConfig config) {
        allowedUrls = config.allowedUrls();
    }

    @Override
    public void filter(DiscFilterResponse response, RequestView request) {
        Optional<String> requestOrigin = request.getFirstHeader("Origin");

        requestOrigin.ifPresent(
                origin -> allowedUrls.stream()
                        .filter(allowedUrl -> matchesRequestOrigin(origin, allowedUrl))
                        .findAny()
                        .ifPresent(allowedOrigin -> setHeaderUnlessExists(response, ALLOW_ORIGIN_HEADER, allowedOrigin))
        );
        ACCESS_CONTROL_HEADERS.forEach((name, value) -> setHeaderUnlessExists(response, name, value));
    }

    private boolean matchesRequestOrigin(String requestOrigin, String allowedUrl) {
        return allowedUrl.equals("*") || requestOrigin.startsWith(allowedUrl);
    }

    /**
     * This is to avoid duplicating headers already set by the {@link AccessControlRequestFilter}.
     * Currently (March 2016), this filter is invoked for OPTIONS requests to jdisc request handlers,
     * even if the request filter has been invoked first. For jersey based APIs, this filter is NOT
     * invoked in these cases.
     */
    private void setHeaderUnlessExists(DiscFilterResponse response, String name, String value) {
        if (response.getHeader(name) == null)
            response.setHeader(name, value);
    }
}
