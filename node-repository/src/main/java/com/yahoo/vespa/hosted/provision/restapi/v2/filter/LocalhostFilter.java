// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.google.common.net.InetAddresses;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.hosted.provision.restapi.v2.ErrorResponse;

import java.net.InetAddress;

/**
 * A security filter that only allows local requests.
 *
 * @author mpolden
 */
@SuppressWarnings("unused") // Injected
public class LocalhostFilter implements SecurityRequestFilter {

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        InetAddress remoteAddr = InetAddresses.forString(request.getRemoteAddr());
        if (!remoteAddr.isLoopbackAddress() && !request.getRemoteAddr().equals(request.getLocalAddr())) {
            FilterUtils.write(ErrorResponse.unauthorized(
                    String.format("%s %s denied for %s: Unauthorized host", request.getMethod(),
                                  request.getUri().getPath(), request.getRemoteAddr())), handler);
        }
    }

}
