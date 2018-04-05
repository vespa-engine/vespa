// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filters.cors;

import com.google.inject.Inject;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.http.filter.DiscFilterResponse;
import com.yahoo.jdisc.http.filter.RequestView;
import com.yahoo.jdisc.http.filter.SecurityResponseFilter;
import com.yahoo.yolean.chain.Provides;

import java.util.HashSet;
import java.util.Set;

import static com.yahoo.jdisc.http.filters.cors.CorsLogic.createCorsResponseHeaders;


/**
 * @author gv
 * @author Tony Vaagenes
 * @author bjorncs
 */
@Provides("CorsSecurityResponseFilter")
public class CorsSecurityResponseFilter extends AbstractResource implements SecurityResponseFilter {

    private final Set<String> allowedUrls;

    @Inject
    public CorsSecurityResponseFilter(CorsSecurityFilterConfig config) {
        this.allowedUrls = new HashSet<>(config.allowedUrls());
    }

    @Override
    public void filter(DiscFilterResponse response, RequestView request) {
        createCorsResponseHeaders(request.getFirstHeader("Origin").orElse(null), allowedUrls)
                .forEach(response::setHeader);
    }

}
