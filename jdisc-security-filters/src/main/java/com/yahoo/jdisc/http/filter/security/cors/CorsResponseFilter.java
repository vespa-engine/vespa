// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.http.filter.DiscFilterResponse;
import com.yahoo.jdisc.http.filter.RequestView;
import com.yahoo.jdisc.http.filter.SecurityResponseFilter;
import com.yahoo.yolean.chain.Provides;

/**
 * @author gv
 * @author Tony Vaagenes
 * @author bjorncs
 */
@Provides("CorsResponseFilter")
public class CorsResponseFilter extends AbstractResource implements SecurityResponseFilter {

    private final CorsLogic cors;

    @Inject
    public CorsResponseFilter(CorsFilterConfig config) {
        this.cors = CorsLogic.forAllowedOrigins(config.allowedUrls());
    }

    @Override
    public void filter(DiscFilterResponse response, RequestView request) {
        cors.createCorsResponseHeaders(request.getFirstHeader("Origin").orElse(null))
                .forEach(response::setHeader);
    }

}
