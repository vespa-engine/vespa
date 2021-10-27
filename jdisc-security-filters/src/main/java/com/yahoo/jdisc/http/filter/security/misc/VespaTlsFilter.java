// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.http.filter.security.misc;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

public class VespaTlsFilter extends JsonSecurityRequestFilterBase {

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        return request.getClientCertificateChain().isEmpty()
                ? Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden to access this path"))
                : Optional.empty();
    }
}
