// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.yolean.chain.After;

/**
 * @author Stian Kristoffersen
 */
@After("BouncerFilter")
public class SetBouncerPassthruHeaderFilter implements SecurityRequestFilter {

    public static final String BOUNCER_PASSTHRU_ATTRIBUTE = "bouncer.bypassthru";
    public static final String BOUNCER_PASSTHRU_COOKIE_OK = "1";
    public static final String BOUNCER_PASSTHRU_HEADER_FIELD = "com.yahoo.hosted.vespa.bouncer.passthru";

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        Object statusProperty = request.getAttribute(BOUNCER_PASSTHRU_ATTRIBUTE);
        String status = Integer.toString((int)statusProperty);

        request.addHeader(BOUNCER_PASSTHRU_HEADER_FIELD, status);
    }

}
