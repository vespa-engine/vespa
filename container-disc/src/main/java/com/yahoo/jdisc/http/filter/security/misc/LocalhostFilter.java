// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.misc;

import com.google.common.net.InetAddresses;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;

import java.net.InetAddress;
import java.util.Optional;

/**
 * A security filter that only allows self-originating requests.
 *
 * @author mpolden
 * @author bjorncs
 */
@SuppressWarnings("unused") // Injected
public class LocalhostFilter extends JsonSecurityRequestFilterBase {

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        InetAddress remoteAddr = InetAddresses.forString(request.getRemoteAddr());
        if (!remoteAddr.isLoopbackAddress() && !request.getRemoteAddr().equals(request.getLocalAddr())) {
            return Optional.of(new ErrorResponse(
                    Response.Status.UNAUTHORIZED,
                    String.format("%s %s denied for %s: Unauthorized host", request.getMethod(),
                                  request.getUri().getPath(), request.getRemoteAddr())));
        }
        return Optional.empty();
    }

}
