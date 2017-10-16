// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.inject.Inject;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;
import com.yahoo.yolean.chain.After;

/**
 * @author bjorncs
 */
@After("BouncerFilter")
public class NTokenRequestFilter implements SecurityRequestFilter {

    public static final String NTOKEN_HEADER = "com.yahoo.vespa.hosted.controller.restapi.filter.NTokenRequestFilter.ntoken";

    private final String principalHeaderName;

    @Inject
    public NTokenRequestFilter(AthenzConfig config) {
        this.principalHeaderName = config.principalHeaderName();
    }

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler responseHandler) {
        String nToken = request.getHeader(principalHeaderName);
        if (nToken != null) {
            request.setAttribute(NTOKEN_HEADER, nToken);
        }
    }
}
