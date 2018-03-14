// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.hosted.provision.restapi.v2.ErrorResponse;

/**
 * A security filter that rejects requests not originating from localhost.
 *
 * @author mpolden
 */
@SuppressWarnings("unused") // Injected
public class LocalhostFilter implements SecurityRequestFilter {

    private static final String inet4Loopback = "127.0.0.1";
    private static final String inet6Loopback = "0:0:0:0:0:0:0:1";

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        switch (request.getRemoteAddr()) {
            case inet4Loopback:
            case inet6Loopback:
                break; // Allowed
            default:
                FilterUtils.write(ErrorResponse.unauthorized(
                        String.format("%s %s denied for %s: Unauthorized host", request.getMethod(),
                                      request.getUri().getPath(), request.getRemoteAddr())), handler);
        }
    }

}
