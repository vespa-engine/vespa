// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.misc;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;

import java.util.Optional;

/**
 * @author bjorncs
 */
public class BlockingRequestFilter extends JsonSecurityRequestFilterBase {
    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden to access this path"));
    }
}
