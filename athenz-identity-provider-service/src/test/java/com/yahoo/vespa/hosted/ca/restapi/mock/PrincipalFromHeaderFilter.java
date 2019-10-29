// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi.mock;

import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzService;

/* Read principal from http header */
public class PrincipalFromHeaderFilter implements SecurityRequestFilter {

    public PrincipalFromHeaderFilter() {}

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        String principal = request.getHeader("PRINCIPAL");
        System.out.println("principal = " + principal);
        request.setUserPrincipal(new AthenzPrincipal(new AthenzService(principal)));
    }
}
