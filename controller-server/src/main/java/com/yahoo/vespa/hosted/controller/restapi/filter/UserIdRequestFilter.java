// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.hosted.controller.api.nonpublic.HeaderFields;
import com.yahoo.yolean.chain.Before;

/**
 * Allows hosts using host-based authentication to set user ID.
 *
 * @author Tony Vaagenes
 */
@Before("CreateSecurityContextFilter")
public class UserIdRequestFilter implements SecurityRequestFilter {

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        String userName = request.getHeader(HeaderFields.USER_ID_HEADER_FIELD);
        request.setUserPrincipal(new UnauthenticatedUserPrincipal(userName));
    }
}
